package com.example.travelplanner.data;

import com.example.travelplanner.model.budget.BudgetTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class BudgetDataRepository {

    private static final Logger log = LoggerFactory.getLogger(BudgetDataRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Key: "destination|tier" → accommodation rate node
    private final Map<String, JsonNode> accommodationRates = new HashMap<>();

    // Key: "destination|tier" → meal cost node
    private final Map<String, JsonNode> mealCosts = new HashMap<>();

    @PostConstruct
    public void load() {
        loadAccommodationRates();
        loadMealCosts();
    }

    private void loadAccommodationRates() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/budget/accommodation-rates.json").getInputStream());
            root.get("rates").forEach(r -> {
                String key = buildKey(r.get("destination").asText(), r.get("tier").asText());
                accommodationRates.put(key, r);
            });
            log.info("Loaded {} accommodation rate entries", accommodationRates.size());
        } catch (Exception e) {
            log.error("Failed to load accommodation rates", e);
        }
    }

    private void loadMealCosts() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/budget/meal-costs.json").getInputStream());
            root.get("mealCosts").forEach(m -> {
                String key = buildKey(m.get("destination").asText(), m.get("tier").asText());
                mealCosts.put(key, m);
            });
            log.info("Loaded {} meal cost entries", mealCosts.size());
        } catch (Exception e) {
            log.error("Failed to load meal costs", e);
        }
    }

    public Optional<Double> getAccommodationPerNight(String destination, BudgetTier tier) {
        return findRate(destination, tier)
                .map(n -> n.get("perNightUSD").asDouble());
    }

    public Optional<String> getAccommodationExamples(String destination, BudgetTier tier) {
        return findRate(destination, tier)
                .map(n -> n.get("examples").asText());
    }

    public Optional<Double> getDailyMealCostPerPerson(String destination, BudgetTier tier) {
        return findMealCost(destination, tier).map(n ->
                n.get("breakfastUSD").asDouble()
                        + n.get("lunchUSD").asDouble()
                        + n.get("dinnerUSD").asDouble()
        );
    }

    public Optional<String> getMealCostNotes(String destination, BudgetTier tier) {
        return findMealCost(destination, tier)
                .map(n -> n.get("notes").asText());
    }

    // Estimates total activity admission cost for a trip duration (rough sum of must-see entry fees)
    public double estimateActivitiesCost(int travelers, double avgAdmissionPerDay, int days) {
        return travelers * avgAdmissionPerDay * days;
    }

    private Optional<JsonNode> findRate(String destination, BudgetTier tier) {
        // Try exact match first, then normalised
        String key = buildKey(destination, tier.name());
        if (accommodationRates.containsKey(key)) return Optional.of(accommodationRates.get(key));
        // Try partial match
        return accommodationRates.entrySet().stream()
                .filter(e -> e.getKey().contains(normalise(destination)) && e.getKey().endsWith("|" + tier.name()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private Optional<JsonNode> findMealCost(String destination, BudgetTier tier) {
        String key = buildKey(destination, tier.name());
        if (mealCosts.containsKey(key)) return Optional.of(mealCosts.get(key));
        return mealCosts.entrySet().stream()
                .filter(e -> e.getKey().contains(normalise(destination)) && e.getKey().endsWith("|" + tier.name()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private String buildKey(String destination, String tier) {
        return normalise(destination) + "|" + tier.toUpperCase();
    }

    private String normalise(String s) {
        return s.toLowerCase().trim();
    }
}
