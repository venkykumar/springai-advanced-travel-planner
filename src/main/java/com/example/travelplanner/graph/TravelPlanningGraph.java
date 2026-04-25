package com.example.travelplanner.graph;

import com.example.travelplanner.agent.BudgetAgent;
import com.example.travelplanner.agent.DestinationResearchAgent;
import com.example.travelplanner.agent.ItineraryBuilderAgent;
import com.example.travelplanner.agent.LogisticsAgent;
import com.example.travelplanner.model.TravelRequest;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.itinerary.DayPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Defines the LangGraph4j StateGraph for human-in-the-loop travel planning.
 *
 * Graph topology:
 *
 *   START
 *     → parse_query            (LLM parses natural language → TravelRequest JSON)
 *     → research_and_estimate  (destination research + logistics + budget, parallel)
 *     → [INTERRUPT BEFORE human_review]   ← client receives budget summary here
 *     → human_review           (optionally recalculates if user adjusted budget)
 *     → build_itinerary        (builds full day-by-day plan)
 *   END
 *
 * The MemorySaver checkpointer persists graph state between the interrupt and
 * the resume call, keyed by threadId.  Swap to PostgresSaver for persistence
 * across server restarts.
 */
@Configuration
public class TravelPlanningGraph {

    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    @Autowired private ChatModel chatModel;
    @Autowired private DestinationResearchAgent destinationAgent;
    @Autowired private LogisticsAgent logisticsAgent;
    @Autowired private BudgetAgent budgetAgent;
    @Autowired private ItineraryBuilderAgent itineraryAgent;
    @Autowired private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("agentExecutor")
    private ExecutorService agentExecutor;

    @Bean
    public CompiledGraph<TravelPlanningState> travelPlanGraph() throws GraphStateException {
        return new StateGraph<>(TravelPlanningState.SCHEMA, TravelPlanningState::new)
                .addNode("parse_query",           this::parseQueryNode)
                .addNode("research_and_estimate", this::researchAndEstimateNode)
                .addNode("human_review",          this::humanReviewNode)
                .addNode("build_itinerary",       this::buildItineraryNode)
                .addEdge(START,                   "parse_query")
                .addEdge("parse_query",           "research_and_estimate")
                .addEdge("research_and_estimate", "human_review")
                .addEdge("human_review",          "build_itinerary")
                .addEdge("build_itinerary",        END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        // Graph pauses here — client inspects budget before proceeding
                        .interruptBefore("human_review")
                        .build());
    }

    // ── Node: parse_query ────────────────────────────────────────────────────

    private CompletableFuture<Map<String, Object>> parseQueryNode(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            String query = state.userQuery()
                    .orElseThrow(() -> new IllegalStateException("userQuery missing from state"));

            String raw = ChatClient.create(chatModel)
                    .prompt()
                    .system("""
                            You parse travel queries into structured JSON. Reply with ONLY valid JSON — no markdown fences, no extra text.

                            Required fields:
                              destination   (string)   — city or country name
                              durationDays  (int)      — number of days
                              travelers     (int)      — number of people
                              budgetUSD     (double)   — total budget for the whole group in USD
                              travelMonth   (string)   — month of travel, default "June"
                              preferences   (string[]) — travel style preferences, e.g. ["culture","food"]
                            """)
                    .user(query)
                    .call()
                    .content();

            return Map.of("travelRequestJson", extractJson(raw));
        }, agentExecutor);
    }

    // ── Node: research_and_estimate ──────────────────────────────────────────

    private CompletableFuture<Map<String, Object>> researchAndEstimateNode(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            TravelRequest req = parseTravelRequest(state);

            // Research and logistics run in parallel; budget runs after both finish
            CompletableFuture<String> researchFuture = CompletableFuture.supplyAsync(
                    () -> destinationAgent.research(req.destination(), req.travelMonth()),
                    agentExecutor);

            CompletableFuture<String> logisticsFuture = CompletableFuture.supplyAsync(
                    () -> logisticsAgent.analyze(req.destination(), req.travelers(), req.travelMonth()),
                    agentExecutor);

            CompletableFuture.allOf(researchFuture, logisticsFuture).join();

            BudgetBreakdown budget = budgetAgent.calculateBudget(
                    req.destination(), req.travelers(), req.durationDays(),
                    req.budgetUSD(), req.travelMonth());

            try {
                return Map.of(
                        "destinationResearch", researchFuture.join(),
                        "logisticsAnalysis",   logisticsFuture.join(),
                        "budgetBreakdownJson", objectMapper.writeValueAsString(budget));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialise budget breakdown", e);
            }
        }, agentExecutor);
    }

    // ── Node: human_review ───────────────────────────────────────────────────
    //
    // This node runs AFTER the interrupt is resolved.
    // If the client called /adjust and added adjustedBudget to state, we
    // recalculate using the new amount.  Otherwise we pass through unchanged.

    private CompletableFuture<Map<String, Object>> humanReviewNode(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            if (state.adjustedBudget().isEmpty()) {
                return Map.of(); // nothing to recalculate
            }

            double newBudget = Double.parseDouble(state.adjustedBudget().get());
            TravelRequest req = parseTravelRequest(state);

            BudgetBreakdown recalculated = budgetAgent.calculateBudget(
                    req.destination(), req.travelers(), req.durationDays(),
                    newBudget, req.travelMonth());

            try {
                return Map.of("budgetBreakdownJson", objectMapper.writeValueAsString(recalculated));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialise recalculated budget", e);
            }
        }, agentExecutor);
    }

    // ── Node: build_itinerary ────────────────────────────────────────────────

    private CompletableFuture<Map<String, Object>> buildItineraryNode(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            TravelRequest req    = parseTravelRequest(state);
            BudgetBreakdown budget = parseBudgetBreakdown(state);
            String research      = state.destinationResearch().orElse("");

            List<DayPlan> days = itineraryAgent.buildItinerary(
                    req.destination(), req.durationDays(), budget, research);

            try {
                return Map.of("itineraryJson", objectMapper.writeValueAsString(days));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialise itinerary", e);
            }
        }, agentExecutor);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    TravelRequest parseTravelRequest(TravelPlanningState state) {
        String json = state.travelRequestJson()
                .orElseThrow(() -> new IllegalStateException("travelRequestJson missing from state"));
        try {
            return objectMapper.readValue(json, TravelRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialise TravelRequest: " + json, e);
        }
    }

    BudgetBreakdown parseBudgetBreakdown(TravelPlanningState state) {
        String json = state.budgetBreakdownJson()
                .orElseThrow(() -> new IllegalStateException("budgetBreakdownJson missing from state"));
        try {
            return objectMapper.readValue(json, BudgetBreakdown.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialise BudgetBreakdown: " + json, e);
        }
    }

    /** Strips markdown code fences if the LLM wrapped its JSON output in them. */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        Matcher m = JSON_FENCE.matcher(trimmed);
        if (m.find()) return m.group(1).strip();
        // Already bare JSON
        int start = trimmed.indexOf('{');
        int end   = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : trimmed;
    }
}
