package com.example.travelplanner.model.logistics;

public record VisaInfo(
        String destination,
        boolean visaRequired,
        String visaType,
        double costUSD,
        int processingDays,
        String notes
) {}
