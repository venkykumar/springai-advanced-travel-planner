package com.example.travelplanner.model;

import java.util.List;

public record TravelRequest(
        String destination,
        int durationDays,
        int travelers,
        double budgetUSD,
        String travelMonth,
        List<String> preferences
) {}