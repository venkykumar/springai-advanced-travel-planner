package com.example.travelplanner.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

/**
 * Shared state that flows through every node in the travel planning graph.
 *
 * All complex objects (TravelRequest, BudgetBreakdown, List<DayPlan>) are stored as
 * JSON strings so they serialise cleanly with the MemorySaver checkpointer without
 * requiring the existing model records to implement Serializable.
 *
 * All channels use the default last-write-wins merge strategy (empty SCHEMA).
 */
public class TravelPlanningState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of();

    public TravelPlanningState(Map<String, Object> data) {
        super(data);
    }

    /** Original natural-language query from the user. */
    public Optional<String> userQuery() { return value("userQuery"); }

    /** Conversation ID for multi-turn context. */
    public Optional<String> conversationId() { return value("conversationId"); }

    /** JSON of TravelRequest — written by parse_query node. */
    public Optional<String> travelRequestJson() { return value("travelRequestJson"); }

    /** Raw text from DestinationResearchAgent — written by research_and_estimate node. */
    public Optional<String> destinationResearch() { return value("destinationResearch"); }

    /** Raw text from LogisticsAgent — written by research_and_estimate node. */
    public Optional<String> logisticsAnalysis() { return value("logisticsAnalysis"); }

    /** JSON of BudgetBreakdown — written by research_and_estimate node, optionally updated by human_review. */
    public Optional<String> budgetBreakdownJson() { return value("budgetBreakdownJson"); }

    /**
     * When set (via updateState before resume), human_review recalculates the
     * budget using this value instead of the original budgetUSD.
     */
    public Optional<String> adjustedBudget() { return value("adjustedBudget"); }

    /** JSON of List&lt;DayPlan&gt; — written by build_itinerary node. */
    public Optional<String> itineraryJson() { return value("itineraryJson"); }
}
