package com.example.travelplanner.model.response;

import com.example.travelplanner.model.TravelRequest;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.itinerary.DayPlan;
import com.example.travelplanner.model.logistics.LogisticsResult;
import com.example.travelplanner.model.trace.AgentTrace;

import java.util.List;

public record TravelPlan(
        String destination,
        TravelRequest travelRequest,
        List<DayPlan> dayPlans,
        BudgetBreakdown budgetBreakdown,
        LogisticsResult logisticsResult,
        List<String> culturalTips,
        List<String> destinationHighlights,
        AgentTrace agentTrace
) {}
