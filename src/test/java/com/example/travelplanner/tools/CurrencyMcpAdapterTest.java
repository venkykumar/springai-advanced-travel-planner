package com.example.travelplanner.tools;

import com.example.travelplanner.data.BudgetDataRepository;
import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.data.LogisticsDataRepository;
import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyMcpAdapterTest {

    private CurrencyMcpAdapter adapter;
    private BudgetBreakdown sampleBreakdown;

    @BeforeEach
    void setUp() {
        // No MCP clients → fallback rates
        adapter = new CurrencyMcpAdapter(List.of());

        // Build a real sample breakdown using the data stack
        DestinationDataRepository destRepo = new DestinationDataRepository();
        destRepo.load();
        BudgetDataRepository budgetRepo = new BudgetDataRepository();
        budgetRepo.load();
        LogisticsDataRepository logisticsRepo = new LogisticsDataRepository();
        logisticsRepo.load();
        BudgetTools tools = new BudgetTools(budgetRepo, logisticsRepo, destRepo);
        sampleBreakdown = tools.calculateInternal("Japan", 2, 7, 8000.0, "october", BudgetTier.MID);
    }

    @Test
    void japanMapsToJpy() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Japan");
        assertThat(enriched.localCurrency()).isEqualTo("JPY");
    }

    @Test
    void franceMapsToEur() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "France (Paris)");
        assertThat(enriched.localCurrency()).isEqualTo("EUR");
    }

    @Test
    void italyMapsToEur() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Italy (Rome & Florence)");
        assertThat(enriched.localCurrency()).isEqualTo("EUR");
    }

    @Test
    void thailandMapsToThb() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Thailand");
        assertThat(enriched.localCurrency()).isEqualTo("THB");
    }

    @Test
    void spainMapsToEur() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Spain (Barcelona)");
        assertThat(enriched.localCurrency()).isEqualTo("EUR");
    }

    @Test
    void jpyFallbackRateMultipliesGrandTotal() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Japan");
        double expectedLocal = Math.round(sampleBreakdown.grandTotal() * CurrencyMcpAdapter.FALLBACK_RATES.get("JPY"));
        assertThat(enriched.localCurrencyTotal()).isEqualTo(expectedLocal);
    }

    @Test
    void eurFallbackRateReducesGrandTotal() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "France (Paris)");
        assertThat(enriched.localCurrencyTotal()).isLessThan(sampleBreakdown.grandTotal());
    }

    @Test
    void exchangeRateStoredOnBreakdown() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Japan");
        assertThat(enriched.exchangeRate()).isEqualTo(CurrencyMcpAdapter.FALLBACK_RATES.get("JPY"));
    }

    @Test
    void unknownDestinationDefaultsToUsd() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Unknown Place");
        assertThat(enriched.localCurrency()).isEqualTo("USD");
        assertThat(enriched.exchangeRate()).isEqualTo(1.0);
        assertThat(enriched.localCurrencyTotal()).isEqualTo(sampleBreakdown.grandTotal());
    }

    @Test
    void destinationLookupIsCaseInsensitive() {
        BudgetBreakdown lower = adapter.enrich(sampleBreakdown, "japan");
        BudgetBreakdown upper = adapter.enrich(sampleBreakdown, "Japan");
        assertThat(lower.localCurrency()).isEqualTo(upper.localCurrency());
    }

    @Test
    void originalFieldsPreservedAfterEnrichment() {
        BudgetBreakdown enriched = adapter.enrich(sampleBreakdown, "Japan");
        assertThat(enriched.tier()).isEqualTo(sampleBreakdown.tier());
        assertThat(enriched.grandTotal()).isEqualTo(sampleBreakdown.grandTotal());
        assertThat(enriched.isWithinBudget()).isEqualTo(sampleBreakdown.isWithinBudget());
        assertThat(enriched.tradeOffNotes()).isEqualTo(sampleBreakdown.tradeOffNotes());
    }
}
