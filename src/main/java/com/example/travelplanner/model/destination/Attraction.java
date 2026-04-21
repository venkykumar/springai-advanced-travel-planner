package com.example.travelplanner.model.destination;

public record Attraction(
        String name,
        String description,
        String neighborhood,
        String region,
        double entryFeeUSD,
        int durationMinutes,
        String category,
        boolean mustSee
) {}