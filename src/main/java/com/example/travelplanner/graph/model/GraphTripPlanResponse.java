package com.example.travelplanner.graph.model;

import com.example.travelplanner.model.TravelRequest;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.itinerary.DayPlan;

import java.util.List;

/**
 * Final response from the LangGraph planning flow (after human approval).
 *
 * Lighter than TravelPlanResponse — omits AgentTrace and LogisticsResult
 * (those belong to the existing orchestrator flow).  The raw logistics
 * summary text is included instead.
 */
public record GraphTripPlanResponse(
        String threadId,
        String conversationId,
        TravelRequest travelRequest,
        List<DayPlan> itinerary,
        BudgetBreakdown budget,
        String logisticsSummary,
        String message
) {}
