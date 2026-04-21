package com.example.travelplanner.model.logistics;

public record FlightEstimate(
        String destination,
        double perPersonUSD,
        double totalForGroupUSD,
        String month,
        String seasonalNotes
) {}
