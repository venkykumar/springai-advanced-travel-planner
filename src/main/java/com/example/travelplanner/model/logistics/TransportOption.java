package com.example.travelplanner.model.logistics;

public record TransportOption(
        String type,
        String name,
        String costDescription,
        String coverage,
        String tip
) {}
