package com.example.travelplanner.model.request;

public record TravelPlanRequest(
        String userQuery,
        String conversationId
) {
    public TravelPlanRequest {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = java.util.UUID.randomUUID().toString();
        }
    }
}