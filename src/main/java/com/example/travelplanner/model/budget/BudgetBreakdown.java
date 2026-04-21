package com.example.travelplanner.model.budget;

public record BudgetBreakdown(
        BudgetTier tier,
        double flightsCostTotal,
        double accommodationPerNight,
        double accommodationTotal,
        double mealsPerDayPerPerson,
        double mealsTotal,
        double activitiesTotal,
        double localTransportTotal,
        double miscBuffer,
        double grandTotal,
        double budgetRemaining,
        boolean isWithinBudget,
        String tradeOffNotes
) {}