package com.example.travelplanner.model.response;

public record TravelPlanResponse(
        String conversationId,
        TravelPlan travelPlan,
        String message
) {}
