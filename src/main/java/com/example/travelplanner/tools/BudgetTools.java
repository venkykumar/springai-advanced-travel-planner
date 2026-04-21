package com.example.travelplanner.tools;

import com.example.travelplanner.data.BudgetDataRepository;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.data.LogisticsDataRepository;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class BudgetTools {

    private final BudgetDataRepository budgetRepo;
    private final LogisticsDataRepository logisticsRepo;
    private final DestinationDataRepository destinationRepo;

    public BudgetTools(BudgetDataRepository budgetRepo,
                       LogisticsDataRepository logisticsRepo,
                       DestinationDataRepository destinationRepo) {
        this.budgetRepo = budgetRepo;
        this.logisticsRepo = logisticsRepo;
        this.destinationRepo = destinationRepo;
    }

    @Tool(description = "Determine the appropriate budget tier (BUDGET/MID/LUXURY) based on available funds, travelers, nights, and destination")
    public String determineBudgetTier(String destination, int travelers, int nights, double totalBudgetUSD, String travelMonth) {
        BudgetBreakdown budget = calculateInternal(destination, travelers, nights, totalBudgetUSD, travelMonth, null);
        return "Recommended tier: " + budget.tier().name() + ". Estimated total: $" + String.format("%.0f", budget.grandTotal())
                + ". Budget remaining: $" + String.format("%.0f", budget.budgetRemaining())
                + ". Within budget: " + budget.isWithinBudget();
    }

    @Tool(description = "Calculate a full cost breakdown for a trip — flights, accommodation, meals, activities, transport, buffer — and determine if within budget")
    public BudgetBreakdown calculateFullBreakdown(String destination, int travelers, int nights, double totalBudgetUSD, String travelMonth) {
        return calculateInternal(destination, travelers, nights, totalBudgetUSD, travelMonth, null);
    }

    @Tool(description = "Recalculate budget breakdown forcing a specific tier (BUDGET, MID, or LUXURY) — use when original tier exceeded budget")
    public BudgetBreakdown calculateBreakdownForTier(String destination, int travelers, int nights, double totalBudgetUSD, String travelMonth, String tier) {
        BudgetTier forcedTier = BudgetTier.valueOf(tier.toUpperCase());
        return calculateInternal(destination, travelers, nights, totalBudgetUSD, travelMonth, forcedTier);
    }

    @Tool(description = "Get accommodation rate and examples for a destination and tier")
    public String getAccommodationInfo(String destination, String tier) {
        BudgetTier bt = BudgetTier.valueOf(tier.toUpperCase());
        double rate = budgetRepo.getAccommodationPerNight(destination, bt).orElse(100.0);
        String examples = budgetRepo.getAccommodationExamples(destination, bt).orElse("Various options available");
        return "Accommodation in " + destination + " (" + tier + "): $" + rate + "/night. Examples: " + examples;
    }

    @Tool(description = "Get daily meal cost estimates per person for a destination and tier")
    public String getMealCostInfo(String destination, String tier) {
        BudgetTier bt = BudgetTier.valueOf(tier.toUpperCase());
        double daily = budgetRepo.getDailyMealCostPerPerson(destination, bt).orElse(40.0);
        String notes = budgetRepo.getMealCostNotes(destination, bt).orElse("Varies by choice");
        return "Daily meals in " + destination + " (" + tier + "): $" + String.format("%.0f", daily)
                + "/person/day. " + notes;
    }

    // Core calculation used by both tool methods
    public BudgetBreakdown calculateInternal(String destination, int travelers, int nights,
                                              double totalBudgetUSD, String month, BudgetTier forcedTier) {
        double flights = logisticsRepo.getFlightEstimate(destination, travelers, month)
                .map(f -> f.totalForGroupUSD()).orElse(700.0 * travelers);

        BudgetTier tier = forcedTier != null ? forcedTier
                : determineTierAutomatically(destination, travelers, nights, totalBudgetUSD, flights, month);

        double accommodationPerNight = budgetRepo.getAccommodationPerNight(destination, tier).orElse(getTierDefault(tier));
        double accommodationTotal = accommodationPerNight * nights;

        double mealsPerDayPerPerson = budgetRepo.getDailyMealCostPerPerson(destination, tier).orElse(getMealDefault(tier));
        double mealsTotal = mealsPerDayPerPerson * travelers * nights;

        double activitiesTotal = estimateActivities(destination, travelers, nights, tier);
        double localTransportTotal = estimateLocalTransport(travelers, nights, tier);
        double subtotal = flights + accommodationTotal + mealsTotal + activitiesTotal + localTransportTotal;
        double miscBuffer = subtotal * 0.08;
        double grandTotal = subtotal + miscBuffer;
        double budgetRemaining = totalBudgetUSD - grandTotal;

        String tradeOffNotes = buildTradeOffNotes(tier, destination, budgetRemaining);

        return new BudgetBreakdown(tier, flights, accommodationPerNight, accommodationTotal,
                mealsPerDayPerPerson, mealsTotal, activitiesTotal, localTransportTotal,
                miscBuffer, grandTotal, budgetRemaining, grandTotal <= totalBudgetUSD, tradeOffNotes);
    }

    private BudgetTier determineTierAutomatically(String destination, int travelers, int nights,
                                                    double budget, double flights, String month) {
        // Try LUXURY first, fall back through MID to BUDGET
        for (BudgetTier tier : new BudgetTier[]{BudgetTier.LUXURY, BudgetTier.MID, BudgetTier.BUDGET}) {
            double accommodation = budgetRepo.getAccommodationPerNight(destination, tier).orElse(getTierDefault(tier)) * nights;
            double meals = budgetRepo.getDailyMealCostPerPerson(destination, tier).orElse(getMealDefault(tier)) * travelers * nights;
            double activities = estimateActivities(destination, travelers, nights, tier);
            double transport = estimateLocalTransport(travelers, nights, tier);
            double total = (flights + accommodation + meals + activities + transport) * 1.08;
            if (total <= budget) return tier;
        }
        return BudgetTier.BUDGET;
    }

    private double estimateActivities(String destination, int travelers, int nights, BudgetTier tier) {
        double avgDailyAdmission = switch (tier) {
            case BUDGET -> 8.0;
            case MID -> 20.0;
            case LUXURY -> 50.0;
        };
        return travelers * avgDailyAdmission * nights;
    }

    private double estimateLocalTransport(int travelers, int nights, BudgetTier tier) {
        double perPersonPerDay = switch (tier) {
            case BUDGET -> 5.0;
            case MID -> 15.0;
            case LUXURY -> 40.0;
        };
        return travelers * perPersonPerDay * nights;
    }

    private double getTierDefault(BudgetTier tier) {
        return switch (tier) {
            case BUDGET -> 60.0;
            case MID -> 150.0;
            case LUXURY -> 350.0;
        };
    }

    private double getMealDefault(BudgetTier tier) {
        return switch (tier) {
            case BUDGET -> 25.0;
            case MID -> 60.0;
            case LUXURY -> 150.0;
        };
    }

    private String buildTradeOffNotes(BudgetTier tier, String destination, double remaining) {
        if (remaining < 0) {
            return "Over budget by $" + String.format("%.0f", Math.abs(remaining))
                    + ". Consider reducing accommodation nights or skipping paid attractions.";
        }
        return switch (tier) {
            case BUDGET -> "Budget tier: capsule hotels/hostels + street food. "
                    + "Splurge on 1-2 signature experiences. $" + String.format("%.0f", remaining) + " buffer.";
            case MID -> "Mid tier: comfortable 3-star hotels + sit-down meals. "
                    + "Good balance of comfort and value. $" + String.format("%.0f", remaining) + " buffer.";
            case LUXURY -> "Luxury tier: 4-5 star hotels + fine dining. "
                    + "Premium experience. $" + String.format("%.0f", remaining) + " buffer.";
        };
    }
}
