package com.example.travelplanner.graph;

import com.example.travelplanner.graph.model.BudgetAdjustRequest;
import com.example.travelplanner.graph.model.GraphTripPlanResponse;
import com.example.travelplanner.graph.model.TripStartResponse;
import com.example.travelplanner.model.TravelRequest;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.itinerary.DayPlan;
import com.example.travelplanner.model.request.TravelPlanRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LangGraph-backed travel planning API.
 *
 * Unlike /api/travel/plan (which runs all agents in one blocking call), this
 * flow pauses after estimating the budget so the user can review before the
 * full itinerary is built.
 *
 * Workflow:
 *   1. POST /api/graph/trips/start
 *        → graph runs parse_query + research_and_estimate, then pauses
 *        → returns threadId + budget summary
 *
 *   2a. POST /api/graph/trips/{threadId}/approve
 *        → graph resumes, builds itinerary, returns full plan
 *
 *   2b. POST /api/graph/trips/{threadId}/adjust  { "newBudgetUSD": 3500 }
 *        → injects adjustedBudget into graph state, resumes, recalculates + builds itinerary
 *
 *   (optional) GET /api/graph/trips/{threadId}/status
 *        → inspect current graph state without advancing it
 */
@RestController
@RequestMapping("/api/graph/trips")
public class GraphTravelController {

    private final CompiledGraph<TravelPlanningState> graph;
    private final TravelPlanningGraph graphConfig;
    private final ObjectMapper objectMapper;

    public GraphTravelController(CompiledGraph<TravelPlanningState> travelPlanGraph,
                                 TravelPlanningGraph graphConfig,
                                 ObjectMapper objectMapper) {
        this.graph        = travelPlanGraph;
        this.graphConfig  = graphConfig;
        this.objectMapper = objectMapper;
    }

    // ── POST /start ──────────────────────────────────────────────────────────

    @PostMapping("/start")
    public TripStartResponse start(@RequestBody TravelPlanRequest request) throws Exception {
        String threadId      = UUID.randomUUID().toString();
        String conversationId = request.conversationId() != null ? request.conversationId() : threadId;

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // Run graph — pauses automatically before human_review due to interruptBefore
        graph.invoke(Map.of(
                "userQuery",      request.userQuery(),
                "conversationId", conversationId
        ), config);

        // Retrieve the checkpointed state at the interruption point
        StateSnapshot<TravelPlanningState> snapshot = graph.getState(config);
        TravelPlanningState state = snapshot.state();

        TravelRequest req    = graphConfig.parseTravelRequest(state);
        BudgetBreakdown budget = graphConfig.parseBudgetBreakdown(state);

        return new TripStartResponse(
                threadId,
                req.destination(),
                req.durationDays(),
                req.travelers(),
                budget.grandTotal(),
                budget.tier().name(),
                budget.isWithinBudget(),
                budget.tradeOffNotes(),
                budget.localCurrency(),
                budget.localCurrencyTotal(),
                budget.exchangeRate(),
                "Budget estimated. Call /approve to build the itinerary or /adjust to change the budget."
        );
    }

    // ── POST /{threadId}/approve ─────────────────────────────────────────────

    @PostMapping("/{threadId}/approve")
    public GraphTripPlanResponse approve(@PathVariable String threadId) throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // Resume from the interruption point — no state change, user accepted the budget
        var result = graph.invoke(GraphInput.resume(), config);

        TravelPlanningState state = result
                .orElseThrow(() -> new IllegalStateException("Graph did not return a final state"));

        return buildFinalResponse(threadId, state);
    }

    // ── POST /{threadId}/adjust ──────────────────────────────────────────────

    @PostMapping("/{threadId}/adjust")
    public GraphTripPlanResponse adjust(@PathVariable String threadId,
                                        @RequestBody BudgetAdjustRequest body) throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // Inject the new budget into the checkpoint before resuming.
        // The human_review node reads this and recalculates BudgetBreakdown.
        RunnableConfig updatedConfig = graph.updateState(
                config,
                Map.of("adjustedBudget", String.valueOf(body.newBudgetUSD())),
                "human_review"
        );

        var result = graph.invoke(GraphInput.resume(), updatedConfig);

        TravelPlanningState state = result
                .orElseThrow(() -> new IllegalStateException("Graph did not return a final state"));

        return buildFinalResponse(threadId, state);
    }

    // ── GET /{threadId}/status ───────────────────────────────────────────────

    @GetMapping("/{threadId}/status")
    public Map<String, Object> status(@PathVariable String threadId) throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        StateSnapshot<TravelPlanningState> snapshot = graph.getState(config);
        TravelPlanningState state = snapshot.state();

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("threadId",          threadId);
        response.put("nextNode",          snapshot.next());
        response.put("hasParsedRequest",  state.travelRequestJson().isPresent());
        response.put("hasBudgetEstimate", state.budgetBreakdownJson().isPresent());
        response.put("hasItinerary",      state.itineraryJson().isPresent());
        response.put("budgetAdjusted",    state.adjustedBudget().isPresent());
        return response;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private GraphTripPlanResponse buildFinalResponse(String threadId,
                                                     TravelPlanningState state) throws Exception {
        TravelRequest   req      = graphConfig.parseTravelRequest(state);
        BudgetBreakdown budget   = graphConfig.parseBudgetBreakdown(state);
        List<DayPlan>   itinerary = parseItinerary(state);
        String          logistics = state.logisticsAnalysis().orElse("");
        String          convId    = state.conversationId().orElse(threadId);

        return new GraphTripPlanResponse(
                threadId, convId, req, itinerary, budget, logistics,
                "Itinerary built successfully.");
    }

    private List<DayPlan> parseItinerary(TravelPlanningState state) throws Exception {
        String json = state.itineraryJson()
                .orElseThrow(() -> new IllegalStateException("itineraryJson missing — graph may not have finished"));
        return objectMapper.readValue(json, new TypeReference<List<DayPlan>>() {});
    }
}
