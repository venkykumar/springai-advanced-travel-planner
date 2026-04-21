package com.example.travelplanner.model.destination;

import java.util.List;

public record Region(
        String name,
        String description,
        List<String> highlights,
        String bestFor
) {}