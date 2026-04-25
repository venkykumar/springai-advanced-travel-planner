package com.example.travelplanner.tools;

import com.example.travelplanner.model.budget.BudgetBreakdown;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Enriches a BudgetBreakdown with local-currency equivalents using the currency MCP server.
 * Falls back to static rates if no MCP client is configured or the server is unreachable.
 *
 * To enable live rates: start currency-mcp-server and set
 * spring.ai.mcp.client.sse.connections.currency-server.url=http://localhost:8090
 */
@Component
public class CurrencyMcpAdapter {

    private static final Logger log = LoggerFactory.getLogger(CurrencyMcpAdapter.class);

    static final Map<String, String> DESTINATION_CURRENCIES = Map.of(
            "japan", "JPY",
            "france (paris)", "EUR",
            "italy (rome & florence)", "EUR",
            "thailand", "THB",
            "spain (barcelona)", "EUR"
    );

    // Approximate fallback rates used when MCP server is not available
    static final Map<String, Double> FALLBACK_RATES = Map.of(
            "JPY", 149.5,
            "EUR", 0.92,
            "THB", 35.8
    );

    private final List<McpSyncClient> mcpClients;

    public CurrencyMcpAdapter(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
        if (mcpClients.isEmpty()) {
            log.info("CurrencyMcpAdapter: no MCP client configured — using static fallback rates");
        } else {
            log.info("CurrencyMcpAdapter: {} MCP client(s) available for live exchange rates", mcpClients.size());
        }
    }

    public BudgetBreakdown enrich(BudgetBreakdown breakdown, String destination) {
        String currencyCode = DESTINATION_CURRENCIES.getOrDefault(destination.toLowerCase(), "USD");
        if ("USD".equals(currencyCode)) {
            return breakdown.withCurrency("USD", breakdown.grandTotal(), 1.0);
        }
        double rate = fetchRate(currencyCode);
        double localTotal = Math.round(breakdown.grandTotal() * rate);
        return breakdown.withCurrency(currencyCode, localTotal, rate);
    }

    private double fetchRate(String currencyCode) {
        if (!mcpClients.isEmpty()) {
            try {
                McpSchema.CallToolResult result = mcpClients.get(0).callTool(
                        new McpSchema.CallToolRequest("getExchangeRate",
                                Map.of("targetCurrency", currencyCode), null));
                if (result != null && !Boolean.TRUE.equals(result.isError()) && !result.content().isEmpty()) {
                    String text = result.content().stream()
                            .filter(c -> c instanceof McpSchema.TextContent)
                            .findFirst()
                            .map(c -> ((McpSchema.TextContent) c).text())
                            .orElse(null);
                    if (text != null) {
                        return Double.parseDouble(text.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("MCP rate fetch failed for {} ({}), using fallback", currencyCode, e.getMessage());
            }
        }
        double fallback = FALLBACK_RATES.getOrDefault(currencyCode, 1.0);
        log.debug("Using fallback rate for {}: {}", currencyCode, fallback);
        return fallback;
    }
}
