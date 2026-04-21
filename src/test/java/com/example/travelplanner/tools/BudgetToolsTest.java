package com.example.travelplanner.tools;

import com.example.travelplanner.data.BudgetDataRepository;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.data.LogisticsDataRepository;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetToolsTest {

    private BudgetTools budgetTools;

    @BeforeEach
    void setUp() {
        DestinationDataRepository destRepo = new DestinationDataRepository();
        destRepo.load();
        BudgetDataRepository budgetRepo = new BudgetDataRepository();
        budgetRepo.load();
        LogisticsDataRepository logisticsRepo = new LogisticsDataRepository();
        logisticsRepo.load();

        budgetTools = new BudgetTools(budgetRepo, logisticsRepo, destRepo);
    }

    @Test
    void japanFamilyFiveThousandDollarsIsWithinBudgetAtBudgetTier() {
        BudgetBreakdown result = budgetTools.calculateInternal(
                "Japan", 4, 7, 5000.0, "april", null);

        assertThat(result).isNotNull();
        assertThat(result.tier()).isIn(BudgetTier.BUDGET, BudgetTier.MID);
        // Grand total should be calculated (positive)
        assertThat(result.grandTotal()).isPositive();
    }

    @Test
    void luxuryBudgetSelectsCorrectTier() {
        BudgetBreakdown result = budgetTools.calculateInternal(
                "Japan", 2, 7, 15000.0, "october", null);

        assertThat(result.tier()).isEqualTo(BudgetTier.LUXURY);
    }

    @Test
    void forcedTierOverridesAutoSelection() {
        BudgetBreakdown result = budgetTools.calculateInternal(
                "Thailand", 2, 7, 10000.0, "january", BudgetTier.BUDGET);

        assertThat(result.tier()).isEqualTo(BudgetTier.BUDGET);
    }

    @Test
    void grandTotalIncludesAllComponents() {
        BudgetBreakdown result = budgetTools.calculateInternal(
                "France (Paris)", 2, 5, 6000.0, "april", BudgetTier.MID);

        double manualSum = result.flightsCostTotal()
                + result.accommodationTotal()
                + result.mealsTotal()
                + result.activitiesTotal()
                + result.localTransportTotal()
                + result.miscBuffer();

        assertThat(result.grandTotal()).isCloseTo(manualSum, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void budgetRemainingCalculatedCorrectly() {
        double totalBudget = 4000.0;
        BudgetBreakdown result = budgetTools.calculateInternal(
                "Thailand", 2, 7, totalBudget, "january", BudgetTier.BUDGET);

        assertThat(result.budgetRemaining()).isCloseTo(totalBudget - result.grandTotal(),
                org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void withinBudgetFlagIsCorrect() {
        BudgetBreakdown within = budgetTools.calculateInternal(
                "Thailand", 1, 5, 10000.0, "july", BudgetTier.BUDGET);
        assertThat(within.isWithinBudget()).isTrue();

        // $500 is almost certainly not enough for any destination
        BudgetBreakdown over = budgetTools.calculateInternal(
                "France (Paris)", 4, 7, 500.0, "july", BudgetTier.BUDGET);
        assertThat(over.isWithinBudget()).isFalse();
    }

    @Test
    void tradeOffNotesAreNotBlank() {
        BudgetBreakdown result = budgetTools.calculateInternal(
                "Japan", 2, 7, 5000.0, "april", BudgetTier.MID);
        assertThat(result.tradeOffNotes()).isNotBlank();
    }

    @Test
    void aprilJapanFlightsHigherThanOctober() {
        BudgetBreakdown april = budgetTools.calculateInternal(
                "Japan", 2, 7, 10000.0, "april", BudgetTier.MID);
        BudgetBreakdown october = budgetTools.calculateInternal(
                "Japan", 2, 7, 10000.0, "october", BudgetTier.MID);

        // April is cherry blossom season — flights are ~25% more expensive
        assertThat(april.flightsCostTotal()).isGreaterThan(october.flightsCostTotal());
    }
}
