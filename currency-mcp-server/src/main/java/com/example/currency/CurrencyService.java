package com.example.currency;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class CurrencyService {

    private final ExchangeRateProvider rateProvider;

    public CurrencyService() {
        RestClient client = RestClient.create("https://api.exchangerate-api.com");
        this.rateProvider = () -> {
            ExchangeRateResponse response = client.get()
                    .uri("/v4/latest/USD")
                    .retrieve()
                    .body(ExchangeRateResponse.class);
            return (response != null && response.rates() != null) ? response.rates() : Map.of();
        };
    }

    // Package-private constructor for testing — inject a fake provider without HTTP calls
    CurrencyService(ExchangeRateProvider rateProvider) {
        this.rateProvider = rateProvider;
    }

    @Tool(description = """
            Get the current exchange rate from USD to the specified currency code.
            Fetches live rates from api.exchangerate-api.com.
            Returns the exchange rate as a decimal number (e.g. 149.5 for JPY, 0.92 for EUR).
            Use 3-letter ISO 4217 currency codes: JPY, EUR, THB, GBP, AUD, CAD, etc.
            Returns 1.0 if the currency code is not found.
            """)
    public double getExchangeRate(String targetCurrency) {
        Map<String, Double> rates = rateProvider.getRates();
        return rates.getOrDefault(targetCurrency.toUpperCase(), 1.0);
    }

    @FunctionalInterface
    interface ExchangeRateProvider {
        Map<String, Double> getRates();
    }

    record ExchangeRateResponse(String base, Map<String, Double> rates) {}
}