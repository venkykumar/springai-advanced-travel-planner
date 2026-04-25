package com.example.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

    private CurrencyService service;

    @BeforeEach
    void setUp() {
        // Inject a fake provider — no real HTTP calls
        Map<String, Double> fakeRates = Map.of("JPY", 149.5, "EUR", 0.92, "THB", 35.8, "GBP", 0.79);
        service = new CurrencyService(() -> fakeRates);
    }

    @Test
    void returnsRateForJpy() {
        double rate = service.getExchangeRate("JPY");
        assertThat(rate).isEqualTo(149.5);
    }

    @Test
    void returnsRateForEur() {
        double rate = service.getExchangeRate("EUR");
        assertThat(rate).isEqualTo(0.92);
    }

    @Test
    void codeIsUpperCasedBeforeLookup() {
        assertThat(service.getExchangeRate("jpy")).isEqualTo(149.5);
        assertThat(service.getExchangeRate("eur")).isEqualTo(0.92);
    }

    @Test
    void unknownCurrencyReturnsFallbackOfOne() {
        double rate = service.getExchangeRate("XYZ");
        assertThat(rate).isEqualTo(1.0);
    }

    @Test
    void emptyRatesReturnFallbackOfOne() {
        CurrencyService emptyService = new CurrencyService(Map::of);
        assertThat(emptyService.getExchangeRate("JPY")).isEqualTo(1.0);
    }

    @Test
    void gbpRateReturned() {
        assertThat(service.getExchangeRate("GBP")).isEqualTo(0.79);
    }
}
