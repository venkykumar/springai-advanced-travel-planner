package com.example.travelplanner.model.destination;

import java.util.List;

public record DestinationResearchResult(
        String destination,
        List<Region> regions,
        List<Attraction> topAttractions,
        SeasonalInfo seasonalInfo,
        List<String> culturalTips,
        List<NeighborhoodGroup> neighborhoodGroups,
        String knowledgeBaseContext
) {}