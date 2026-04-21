package com.example.travelplanner.agent;

import com.example.travelplanner.data.BudgetDataRepository;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.data.LogisticsDataRepository;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import com.example.travelplanner.tools.BudgetTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BudgetAgent tests — no LLM calls, BudgetAgent delegates directly to BudgetTools.
 */
class BudgetAgentTest {

    private BudgetAgent budgetAgent;

    @BeforeEach
    void setUp() {
        DestinationDataRepository destRepo = new DestinationDataRepository();
        destRepo.load();
        BudgetDataRepository budgetRepo = new BudgetDataRepository();
        budgetRepo.load();
        LogisticsDataRepository logisticsRepo = new LogisticsDataRepository();
        logisticsRepo.load();
        BudgetTools tools = new BudgetTools(budgetRepo, logisticsRepo, destRepo);
        budgetAgent = new BudgetAgent(tools);
    }

    @Test
    void calculateBudgetReturnsNonNull() {
        BudgetBreakdown bd = budgetAgent.calculateBudget("Japan", 4, 7, 5000.0, "april");
        assertThat(bd).isNotNull();
    }

    @Test
    void budgetValidationLoopLogic_overBudgetThenRetry() {
        // Force MID tier on a tight budget — this will be over budget
        BudgetBreakdown midTier = budgetAgent.recalculateForTier(
                "France (Paris)", 4, 7, 2000.0, "july", BudgetTier.MID);
        assertThat(midTier.tier()).isEqualTo(BudgetTier.MID);
        assertThat(midTier.isWithinBudget()).isFalse();

        // Simulate orchestrator's retry — downgrade to BUDGET
        BudgetBreakdown retry = budgetAgent.recalculateForTier(
                "France (Paris)", 4, 7, 2000.0, "july", BudgetTier.BUDGET);
        assertThat(retry.grandTotal()).isLessThan(midTier.grandTotal());
        assertThat(retry.tier()).isEqualTo(BudgetTier.BUDGET);
    }

    @Test
    void recalculateForTierForcesRequestedTier() {
        BudgetBreakdown bd = budgetAgent.recalculateForTier(
                "Thailand", 2, 5, 10000.0, "january", BudgetTier.LUXURY);
        assertThat(bd.tier()).isEqualTo(BudgetTier.LUXURY);
    }

    @Test
    void tierOrderingIsRespected() {
        BudgetBreakdown budget = budgetAgent.recalculateForTier("Spain (Barcelona)", 2, 6, 20000.0, "july", BudgetTier.BUDGET);
        BudgetBreakdown mid = budgetAgent.recalculateForTier("Spain (Barcelona)", 2, 6, 20000.0, "july", BudgetTier.MID);
        BudgetBreakdown luxury = budgetAgent.recalculateForTier("Spain (Barcelona)", 2, 6, 20000.0, "july", BudgetTier.LUXURY);

        assertThat(budget.grandTotal()).isLessThan(mid.grandTotal());
        assertThat(mid.grandTotal()).isLessThan(luxury.grandTotal());
    }

    @Test
    void allDestinationsCanBeCalculated() {
        for (String dest : new String[]{"Japan", "France (Paris)", "Italy (Rome & Florence)", "Thailand", "Spain (Barcelona)"}) {
            BudgetBreakdown bd = budgetAgent.calculateBudget(dest, 2, 7, 8000.0, "october");
            assertThat(bd).as("Budget calculation failed for: " + dest).isNotNull();
            assertThat(bd.grandTotal()).as("Grand total should be positive for: " + dest).isPositive();
        }
    }
}
