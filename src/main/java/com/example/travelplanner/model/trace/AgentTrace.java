package com.example.travelplanner.model.trace;

import java.util.List;

public record AgentTrace(
        List<TraceStep> steps,
        long totalDurationMs,
        int parallelTaskCount,
        int budgetRetriesUsed
) {}
