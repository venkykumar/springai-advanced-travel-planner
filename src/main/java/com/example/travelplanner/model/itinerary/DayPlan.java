package com.example.travelplanner.model.itinerary;

import java.util.List;

public record DayPlan(
        int dayNumber,
        String theme,
        String region,
        List<String> morningActivities,
        List<String> afternoonActivities,
        List<String> eveningActivities,
        String lunchRecommendation,
        String dinnerRecommendation,
        String accommodation,
        double estimatedDailySpendPerPerson,
        String notes
) {}
