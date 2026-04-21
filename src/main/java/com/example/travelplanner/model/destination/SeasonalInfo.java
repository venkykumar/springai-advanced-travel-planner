package com.example.travelplanner.model.destination;

import java.util.List;

public record SeasonalInfo(
        String month,
        String weather,
        String crowdLevel,
        List<String> notableEvents,
        List<String> packingTips,
        List<String> pros,
        List<String> cons
) {}