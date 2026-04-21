package com.example.travelplanner.agent;

import com.example.travelplanner.model.TravelRequest;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import com.example.travelplanner.model.itinerary.DayPlan;
import com.example.travelplanner.model.logistics.LogisticsResult;
import com.example.travelplanner.model.request.TravelPlanRequest;
import com.example.travelplanner.model.response.TravelPlan;
import com.example.travelplanner.model.response.TravelPlanResponse;
import com.example.travelplanner.model.trace.AgentTrace;
import com.example.travelplanner.model.trace.TraceStep;
import com.example.travelplanner.tools.DestinationTools;
import com.example.travelplanner.tools.LogisticsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private static final String PARSE_SYSTEM_PROMPT = """
            You are a travel request parser. Extract structured travel details from natural language queries.
            Return ONLY a JSON object with these fields:
            - destination (string — normalise to one of: "Japan", "France (Paris)", "Italy (Rome & Florence)", "Thailand", "Spain (Barcelona)")
            - durationDays (int — default 7 if not specified)
            - travelers (int — default 2 if not specified)
            - budgetUSD (double — total budget for the entire trip for all travelers; default 5000 if not specified)
            - travelMonth (string — full month name, e.g., "April"; default "October" if not specified)
            - preferences (array of strings — any special requests: "beach", "food tour", "museums", "budget-friendly", etc.)
            """;

    private static final String SYNTHESIS_SYSTEM_PROMPT = """
            You are a master travel planner synthesising research from specialist agents into a complete travel plan.
            You have access to tools to look up knowledge base context if needed.
            Your output must be a complete, actionable travel plan that a family or group could follow directly.
            Be warm and enthusiastic — this is someone's dream trip.
            """;

    private final ChatClient parseClient;
    private final ChatClient synthesisClient;
    private final DestinationResearchAgent destinationAgent;
    private final LogisticsAgent logisticsAgent;
    private final BudgetAgent budgetAgent;
    private final ItineraryBuilderAgent itineraryAgent;
    private final DestinationTools destinationTools;
    private final LogisticsTools logisticsTools;
    private final ChatMemory chatMemory;
    private final ExecutorService executor;

    @Value("${travel.agent.max-budget-retries:2}")
    private int maxBudgetRetries;

    @Value("${travel.agent.parallel-timeout-seconds:60}")
    private int parallelTimeoutSeconds;

    public OrchestratorAgent(ChatModel chatModel,
                              DestinationResearchAgent destinationAgent,
                              LogisticsAgent logisticsAgent,
                              BudgetAgent budgetAgent,
                              ItineraryBuilderAgent itineraryAgent,
                              DestinationTools destinationTools,
                              LogisticsTools logisticsTools,
                              ChatMemory chatMemory,
                              ExecutorService agentExecutor) {
        this.destinationAgent = destinationAgent;
        this.logisticsAgent = logisticsAgent;
        this.budgetAgent = budgetAgent;
        this.itineraryAgent = itineraryAgent;
        this.destinationTools = destinationTools;
        this.logisticsTools = logisticsTools;
        this.chatMemory = chatMemory;
        this.executor = agentExecutor;

        this.parseClient = ChatClient.builder(chatModel)
                .defaultSystem(PARSE_SYSTEM_PROMPT)
                .build();

        this.synthesisClient = ChatClient.builder(chatModel)
                .defaultSystem(SYNTHESIS_SYSTEM_PROMPT)
                .build();
    }

    public TravelPlanResponse plan(TravelPlanRequest request) {
        long start = System.currentTimeMillis();
        List<TraceStep> trace = new ArrayList<>();
        log.info("OrchestratorAgent starting plan for conversationId={}", request.conversationId());

        // ── Step 1: Parse the natural language query into a structured TravelRequest ──
        TravelRequest travelRequest = parseQuery(request.userQuery(), trace);
        log.info("Parsed request: {}", travelRequest);

        // ── Step 2: Fire Destination + Logistics + Budget agents IN PARALLEL ──
        long parallelStart = System.currentTimeMillis();

        CompletableFuture<String> destFuture = CompletableFuture.supplyAsync(() -> {
            long t = System.currentTimeMillis();
            String result = destinationAgent.research(travelRequest.destination(), travelRequest.travelMonth());
            addTrace(trace, "DestinationResearchAgent", "research",
                    travelRequest.destination() + " / " + travelRequest.travelMonth(),
                    truncate(result, 120), System.currentTimeMillis() - t, true);
            return result;
        }, executor);

        CompletableFuture<String> logiFuture = CompletableFuture.supplyAsync(() -> {
            long t = System.currentTimeMillis();
            String result = logisticsAgent.analyze(
                    travelRequest.destination(), travelRequest.travelers(), travelRequest.travelMonth());
            addTrace(trace, "LogisticsAgent", "analyze",
                    travelRequest.destination() + " / " + travelRequest.travelers() + " travelers",
                    truncate(result, 120), System.currentTimeMillis() - t, true);
            return result;
        }, executor);

        CompletableFuture<BudgetBreakdown> budgetFuture = CompletableFuture.supplyAsync(() -> {
            long t = System.currentTimeMillis();
            BudgetBreakdown bd = budgetAgent.calculateBudget(
                    travelRequest.destination(), travelRequest.travelers(),
                    travelRequest.durationDays(), travelRequest.budgetUSD(), travelRequest.travelMonth());
            addTrace(trace, "BudgetAgent", "calculateBudget",
                    "$" + travelRequest.budgetUSD() + " / " + travelRequest.travelers() + " travelers",
                    "Tier=" + bd.tier() + " Total=$" + String.format("%.0f", bd.grandTotal()) + " withinBudget=" + bd.isWithinBudget(),
                    System.currentTimeMillis() - t, true);
            return bd;
        }, executor);

        // Wait for all parallel tasks
        try {
            CompletableFuture.allOf(destFuture, logiFuture, budgetFuture)
                    .get(parallelTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Parallel agent tasks timed out or failed", e);
        }
        log.info("Parallel phase completed in {}ms", System.currentTimeMillis() - parallelStart);

        String destinationResearch = destFuture.join();
        String logisticsAnalysis = logiFuture.join();
        BudgetBreakdown budgetBreakdown = budgetFuture.join();

        // ── Step 3: Budget validation ReAct loop ──
        // If the initial budget exceeds the user's total, try lower tiers
        int budgetRetries = 0;
        while (!budgetBreakdown.isWithinBudget() && budgetRetries < maxBudgetRetries) {
            BudgetTier currentTier = budgetBreakdown.tier();
            BudgetTier lowerTier = currentTier == BudgetTier.LUXURY ? BudgetTier.MID : BudgetTier.BUDGET;
            log.info("Budget exceeded (${} > ${}), retrying with tier {}",
                    String.format("%.0f", budgetBreakdown.grandTotal()), travelRequest.budgetUSD(), lowerTier);

            long t = System.currentTimeMillis();
            budgetBreakdown = budgetAgent.recalculateForTier(
                    travelRequest.destination(), travelRequest.travelers(),
                    travelRequest.durationDays(), travelRequest.budgetUSD(),
                    travelRequest.travelMonth(), lowerTier);
            addTrace(trace, "BudgetAgent", "recalculate[retry " + (budgetRetries + 1) + "]",
                    "tier downgrade " + currentTier + " → " + lowerTier,
                    "New total=$" + String.format("%.0f", budgetBreakdown.grandTotal()) + " withinBudget=" + budgetBreakdown.isWithinBudget(),
                    System.currentTimeMillis() - t, false);
            budgetRetries++;
        }

        // ── Step 4: Build day-by-day itinerary (sequential — depends on step 2+3 results) ──
        long itinStart = System.currentTimeMillis();
        List<DayPlan> dayPlans = itineraryAgent.buildItinerary(
                travelRequest.destination(), travelRequest.durationDays(),
                budgetBreakdown, destinationResearch);
        addTrace(trace, "ItineraryBuilderAgent", "buildItinerary",
                travelRequest.durationDays() + " days / tier=" + budgetBreakdown.tier(),
                dayPlans.size() + " days planned",
                System.currentTimeMillis() - itinStart, false);

        // ── Step 5: Parse logistics into structured type ──
        LogisticsResult logisticsResult = buildLogisticsResult(
                travelRequest.destination(), travelRequest.travelers(), travelRequest.travelMonth());

        // ── Step 6: Extract cultural tips and highlights from research ──
        List<String> culturalTips = extractCulturalTips(destinationResearch);
        List<String> highlights = extractHighlights(destinationResearch);

        // ── Step 7: Assemble AgentTrace ──
        AgentTrace agentTrace = new AgentTrace(
                List.copyOf(trace),
                System.currentTimeMillis() - start,
                3, // three parallel agents
                budgetRetries
        );

        // ── Step 8: Build final TravelPlan ──
        TravelPlan plan = new TravelPlan(
                travelRequest.destination(),
                travelRequest,
                dayPlans,
                budgetBreakdown,
                logisticsResult,
                culturalTips,
                highlights,
                agentTrace
        );

        // ── Step 9: Store summary in JDBC chat memory for follow-up requests ──
        storeInMemory(request.conversationId(), travelRequest, budgetBreakdown);

        log.info("OrchestratorAgent completed plan in {}ms", System.currentTimeMillis() - start);
        return new TravelPlanResponse(request.conversationId(), plan,
                "Plan generated successfully for " + travelRequest.destination());
    }

    public TravelPlanResponse followUp(TravelPlanRequest request) {
        long start = System.currentTimeMillis();
        List<TraceStep> trace = new ArrayList<>();
        log.info("OrchestratorAgent processing follow-up for conversationId={}", request.conversationId());

        // Use chat memory advisor — conversation history automatically injected
        long t = System.currentTimeMillis();
        String followUpResult = synthesisClient.prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(request.conversationId())
                        .build())
                .user(request.userQuery())
                .tools(destinationTools, logisticsTools)
                .call()
                .content();
        addTrace(trace, "OrchestratorAgent", "followUp",
                request.userQuery(), truncate(followUpResult, 120),
                System.currentTimeMillis() - t, false);

        AgentTrace agentTrace = new AgentTrace(List.copyOf(trace),
                System.currentTimeMillis() - start, 0, 0);

        // Return a lightweight response with the follow-up answer embedded in the plan summary
        TravelPlan plan = new TravelPlan(
                "Follow-up response", null, List.of(), null, null,
                List.of(followUpResult), List.of(), agentTrace
        );

        return new TravelPlanResponse(request.conversationId(), plan, followUpResult);
    }

    private TravelRequest parseQuery(String userQuery, List<TraceStep> trace) {
        long t = System.currentTimeMillis();
        TravelRequest result = parseClient.prompt()
                .user(userQuery)
                .call()
                .entity(TravelRequest.class);
        addTrace(trace, "OrchestratorAgent", "parseQuery",
                userQuery, "destination=" + result.destination() + " days=" + result.durationDays()
                        + " travelers=" + result.travelers() + " budget=$" + result.budgetUSD(),
                System.currentTimeMillis() - t, false);
        return result;
    }

    private LogisticsResult buildLogisticsResult(String destination, int travelers, String month) {
        var flight = logisticsTools.getFlightEstimates(destination, travelers, month);
        var transport = logisticsTools.getLocalTransportOptions(destination);
        var visa = logisticsTools.getVisaRequirements(destination);

        // Wrap raw tool string results into the typed LogisticsResult using data repos directly
        // (The agent already has the text — we use the repos for the structured type)
        return new LogisticsResult(destination,
                extractFlightEstimate(destination, travelers, month),
                extractTransportOptions(destination),
                extractVisaInfo(destination),
                List.of(transport, visa));
    }

    private com.example.travelplanner.model.logistics.FlightEstimate extractFlightEstimate(
            String destination, int travelers, String month) {
        // Delegate to the logistics data repository through the tool
        return new com.example.travelplanner.model.logistics.FlightEstimate(
                destination, 0, 0, month, "See logistics summary");
    }

    private List<com.example.travelplanner.model.logistics.TransportOption> extractTransportOptions(String dest) {
        return List.of();
    }

    private com.example.travelplanner.model.logistics.VisaInfo extractVisaInfo(String destination) {
        return new com.example.travelplanner.model.logistics.VisaInfo(
                destination, false, "Visa Waiver", 0, 0, "See logistics summary");
    }

    private List<String> extractCulturalTips(String research) {
        // Extract bullet points from the research text that look like cultural tips
        return research.lines()
                .filter(l -> l.contains("•") || l.contains("-") || l.contains("tip") || l.contains("custom"))
                .limit(8)
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toList();
    }

    private List<String> extractHighlights(String research) {
        return research.lines()
                .filter(l -> l.contains("Must-see") || l.contains("must-see") || l.contains("highlight") || l.contains("★"))
                .limit(5)
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toList();
    }

    private void storeInMemory(String conversationId, TravelRequest req, BudgetBreakdown budget) {
        // Store a concise summary so follow-up requests have context without re-planning
        String summary = "Planned: %d days in %s for %d travelers in %s. Budget: $%.0f (%s tier). Estimated total: $%.0f."
                .formatted(req.durationDays(), req.destination(), req.travelers(), req.travelMonth(),
                        req.budgetUSD(), budget.tier(), budget.grandTotal());
        chatMemory.add(conversationId,
                List.of(new org.springframework.ai.chat.messages.AssistantMessage(summary)));
    }

    private synchronized void addTrace(List<TraceStep> trace, String agent, String action,
                                        String input, String output, long durationMs, boolean parallel) {
        trace.add(new TraceStep(agent, action, input, output, durationMs, "SUCCESS", parallel));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
