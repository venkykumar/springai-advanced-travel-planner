package com.example.travelplanner.model.trace;

public record TraceStep(
        String agentName,
        String action,
        String inputSummary,
        String outputSummary,
        long durationMs,
        String status,
        boolean parallel
) {}
