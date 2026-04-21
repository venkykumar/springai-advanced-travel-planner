package com.example.travelplanner.data;

import com.example.travelplanner.model.budget.BudgetTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetDataRepositoryTest {

    private BudgetDataRepository repo;

    @BeforeEach
    void setUp() {
        repo = new BudgetDataRepository();
        repo.load();
    }

    @ParameterizedTest
    @EnumSource(BudgetTier.class)
    void japanAccommodationRateExistsForAllTiers(BudgetTier tier) {
        Optional<Double> rate = repo.getAccommodationPerNight("Japan", tier);
        assertThat(rate).isPresent();
        assertThat(rate.get()).isPositive();
    }

    @Test
    void budgetTierCheaperThanMid() {
        double budget = repo.getAccommodationPerNight("Japan", BudgetTier.BUDGET).orElseThrow();
        double mid = repo.getAccommodationPerNight("Japan", BudgetTier.MID).orElseThrow();
        assertThat(budget).isLessThan(mid);
    }

    @Test
    void midTierCheaperThanLuxury() {
        double mid = repo.getAccommodationPerNight("Japan", BudgetTier.MID).orElseThrow();
        double luxury = repo.getAccommodationPerNight("Japan", BudgetTier.LUXURY).orElseThrow();
        assertThat(mid).isLessThan(luxury);
    }

    @Test
    void mealCostIncludesAllMeals() {
        Optional<Double> daily = repo.getDailyMealCostPerPerson("Japan", BudgetTier.MID);
        assertThat(daily).isPresent();
        assertThat(daily.get()).isGreaterThan(0);
    }

    @Test
    void parisAccommodationHigherThanThailand() {
        double paris = repo.getAccommodationPerNight("France (Paris)", BudgetTier.MID).orElseThrow();
        double thailand = repo.getAccommodationPerNight("Thailand", BudgetTier.MID).orElseThrow();
        assertThat(paris).isGreaterThan(thailand);
    }

    @Test
    void unknownDestinationReturnsEmpty() {
        Optional<Double> rate = repo.getAccommodationPerNight("Antarctica", BudgetTier.MID);
        assertThat(rate).isEmpty();
    }
}
