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
        String tradeOffNotes,
        String localCurrency,
        double localCurrencyTotal,
        double exchangeRate
) {
    public BudgetBreakdown withCurrency(String currency, double localTotal, double rate) {
        return new BudgetBreakdown(tier, flightsCostTotal, accommodationPerNight,
                accommodationTotal, mealsPerDayPerPerson, mealsTotal, activitiesTotal,
                localTransportTotal, miscBuffer, grandTotal, budgetRemaining,
                isWithinBudget, tradeOffNotes, currency, localTotal, rate);
    }
}
