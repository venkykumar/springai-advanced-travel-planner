package com.example.travelplanner.graph.model;

/**
 * Request body for POST /api/graph/trips/{threadId}/adjust.
 * Supplies the revised total budget in USD; the graph recalculates before building the itinerary.
 */
public record BudgetAdjustRequest(double newBudgetUSD) {}
