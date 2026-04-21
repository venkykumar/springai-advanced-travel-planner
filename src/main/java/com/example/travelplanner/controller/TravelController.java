package com.example.travelplanner.controller;

import com.example.travelplanner.agent.OrchestratorAgent;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.model.request.TravelPlanRequest;
import com.example.travelplanner.model.response.TravelPlanResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private final OrchestratorAgent orchestrator;
    private final DestinationDataRepository destinationRepo;

    public TravelController(OrchestratorAgent orchestrator, DestinationDataRepository destinationRepo) {
        this.orchestrator = orchestrator;
        this.destinationRepo = destinationRepo;
    }

    /**
     * Plan a new trip from a natural language query.
     * A new conversationId is auto-generated if not provided — store it for follow-ups.
     *
     * Example:
     *   POST /api/travel/plan
     *   {"userQuery": "Plan a 7-day trip to Japan in April for a family of 4 with a $5000 budget"}
     */
    @PostMapping("/plan")
    public TravelPlanResponse plan(@RequestBody TravelPlanRequest request) {
        return orchestrator.plan(request);
    }

    /**
     * Follow-up on an existing plan using its conversationId.
     * The JDBC chat memory ensures the previous plan context is automatically loaded.
     *
     * Example:
     *   POST /api/travel/followup
     *   {"userQuery": "Make day 3 a food tour", "conversationId": "abc-123"}
     */
    @PostMapping("/followup")
    public TravelPlanResponse followUp(@RequestBody TravelPlanRequest request) {
        return orchestrator.followUp(request);
    }

    /**
     * Quick plan via query params for easy browser/curl testing.
     *
     * Example:
     *   GET /api/travel/plan/quick?query=Plan+7+days+in+Japan&conversationId=test-1
     */
    @GetMapping("/plan/quick")
    public TravelPlanResponse quickPlan(
            @RequestParam String query,
            @RequestParam(required = false) String conversationId) {
        String convId = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
        return orchestrator.plan(new TravelPlanRequest(query, convId));
    }

    /**
     * List supported destinations.
     */
    @GetMapping("/destinations")
    public Map<String, List<String>> destinations() {
        return Map.of("destinations", destinationRepo.supportedDestinations());
    }

    /**
     * Health check / demo info endpoint.
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "Travel Itinerary Planner — Spring AI Multi-Agent Demo",
                "agents", List.of(
                        "OrchestratorAgent — coordinates all agents, budget validation loop, JDBC memory",
                        "DestinationResearchAgent — regions, attractions, seasonal tips via PgVectorStore RAG",
                        "LogisticsAgent — flights, transport, visa requirements",
                        "BudgetAgent — tier selection, cost breakdown, ReAct budget loop",
                        "ItineraryBuilderAgent — day-by-day schedule with restaurant picks"
                ),
                "features", List.of(
                        "Parallel agent execution (CompletableFuture)",
                        "Budget validation ReAct loop",
                        "JDBC-backed chat memory (multi-turn follow-ups survive restarts)",
                        "PgVectorStore RAG (production-grade HNSW embeddings)",
                        "Structured output (TravelPlan, DayPlan, BudgetBreakdown)",
                        "AgentTrace — every step visible in response"
                ),
                "supportedDestinations", destinationRepo.supportedDestinations(),
                "exampleQuery", "Plan a 7-day trip to Japan in April for a family of 4 with a $5000 budget"
        );
    }
}
