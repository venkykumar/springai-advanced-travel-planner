package com.example.travelplanner.graph.model;

/**
 * Returned by POST /api/graph/trips/start.
 *
 * The graph has paused before the human_review node.
 * The client can inspect the budget estimate and then either:
 *   - POST /{threadId}/approve  — accept as-is
 *   - POST /{threadId}/adjust   — supply a new budget and recalculate
 */
public record TripStartResponse(
        String threadId,
        String destination,
        int durationDays,
        int travelers,
        double budgetEstimateUSD,
        String budgetTier,
        boolean isWithinBudget,
        String tradeOffNotes,
        String localCurrency,
        double localCurrencyTotal,
        double exchangeRate,
        String message
) {}
