package com.example.travelplanner.agent;

import com.example.travelplanner.model.budget.BudgetBreakdown;
import com.example.travelplanner.model.budget.BudgetTier;
import com.example.travelplanner.tools.BudgetTools;
import com.example.travelplanner.tools.CurrencyMcpAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BudgetAgent {

    private static final Logger log = LoggerFactory.getLogger(BudgetAgent.class);

    private final BudgetTools budgetTools;
    private final CurrencyMcpAdapter currencyAdapter;

    public BudgetAgent(BudgetTools budgetTools, CurrencyMcpAdapter currencyAdapter) {
        this.budgetTools = budgetTools;
        this.currencyAdapter = currencyAdapter;
    }

    public BudgetBreakdown calculateBudget(String destination, int travelers, int nights,
                                            double totalBudgetUSD, String travelMonth) {
        log.debug("BudgetAgent calculating budget: {} travelers, {} nights, ${} in {}",
                travelers, nights, totalBudgetUSD, destination);
        BudgetBreakdown breakdown = budgetTools.calculateInternal(
                destination, travelers, nights, totalBudgetUSD, travelMonth, null);
        return currencyAdapter.enrich(breakdown, destination);
    }

    public BudgetBreakdown recalculateForTier(String destination, int travelers, int nights,
                                               double totalBudgetUSD, String travelMonth, BudgetTier tier) {
        log.debug("BudgetAgent recalculating for tier {}: {} travelers, {} nights in {}",
                tier, travelers, nights, destination);
        BudgetBreakdown breakdown = budgetTools.calculateInternal(
                destination, travelers, nights, totalBudgetUSD, travelMonth, tier);
        return currencyAdapter.enrich(breakdown, destination);
    }
}
