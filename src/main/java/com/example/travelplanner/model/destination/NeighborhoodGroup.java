package com.example.travelplanner.model.destination;

import java.util.List;

public record NeighborhoodGroup(
        String name,
        List<String> attractions
) {}