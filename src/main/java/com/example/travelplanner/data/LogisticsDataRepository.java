package com.example.travelplanner.data;

import com.example.travelplanner.model.logistics.FlightEstimate;
import com.example.travelplanner.model.logistics.TransportOption;
import com.example.travelplanner.model.logistics.VisaInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class LogisticsDataRepository {

    private static final Logger log = LoggerFactory.getLogger(LogisticsDataRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Key: normalised destination
    private final Map<String, JsonNode> flightData = new HashMap<>();
    private final Map<String, List<JsonNode>> transportData = new HashMap<>();
    private final Map<String, JsonNode> visaData = new HashMap<>();

    @PostConstruct
    public void load() {
        loadFlightEstimates();
        loadTransportOptions();
        loadVisaRequirements();
    }

    private void loadFlightEstimates() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/logistics/flight-estimates.json").getInputStream());
            root.get("flightEstimates").forEach(f -> {
                String key = normalise(f.get("destination").asText());
                flightData.put(key, f);
            });
            log.info("Loaded {} flight estimate entries", flightData.size());
        } catch (Exception e) {
            log.error("Failed to load flight estimates", e);
        }
    }

    private void loadTransportOptions() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/logistics/transport-options.json").getInputStream());
            root.get("transportOptions").forEach(t -> {
                String key = normalise(t.get("destination").asText());
                transportData.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            });
            log.info("Loaded transport options for {} destinations", transportData.size());
        } catch (Exception e) {
            log.error("Failed to load transport options", e);
        }
    }

    private void loadVisaRequirements() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("data/logistics/visa-requirements.json").getInputStream());
            root.get("visaRequirements").forEach(v -> {
                String key = normalise(v.get("destination").asText());
                visaData.put(key, v);
            });
            log.info("Loaded {} visa requirement entries", visaData.size());
        } catch (Exception e) {
            log.error("Failed to load visa requirements", e);
        }
    }

    public Optional<FlightEstimate> getFlightEstimate(String destination, int travelers, String month) {
        JsonNode node = findByDestination(flightData, destination);
        if (node == null) return Optional.empty();
        double perPerson = node.get("perPersonEconomyUSD").asDouble();
        // April in Japan and July in Paris have 20-30% surcharges
        double multiplier = getSeasonalMultiplier(destination, month);
        double adjustedPerPerson = perPerson * multiplier;
        double total = adjustedPerPerson * travelers;
        return Optional.of(new FlightEstimate(
                destination,
                Math.round(adjustedPerPerson),
                Math.round(total),
                month,
                node.get("seasonalNotes").asText()
        ));
    }

    public List<TransportOption> getTransportOptions(String destination) {
        List<JsonNode> nodes = findListByDestination(transportData, destination);
        return nodes.stream().map(t -> new TransportOption(
                t.get("type").asText(),
                t.get("name").asText(),
                t.get("costDescription").asText(),
                t.get("coverage").asText(),
                t.get("tip").asText()
        )).toList();
    }

    public Optional<VisaInfo> getVisaInfo(String destination) {
        JsonNode node = findByDestination(visaData, destination);
        if (node == null) return Optional.empty();
        return Optional.of(new VisaInfo(
                destination,
                node.get("visaRequired").asBoolean(),
                node.get("visaType").asText(),
                node.get("costUSD").asDouble(),
                node.get("processingDays").asInt(),
                node.get("notes").asText()
        ));
    }

    private double getSeasonalMultiplier(String destination, String month) {
        String dest = normalise(destination);
        String m = month.toLowerCase();
        if (dest.contains("japan") && (m.contains("april") || m.contains("mar"))) return 1.25;
        if (dest.contains("france") && (m.contains("july") || m.contains("aug"))) return 1.25;
        if (dest.contains("italy") && m.contains("april")) return 1.15;
        if (dest.contains("spain") && (m.contains("july") || m.contains("aug"))) return 1.30;
        if (dest.contains("thailand") && (m.contains("dec") || m.contains("jan"))) return 1.20;
        return 1.0;
    }

    private JsonNode findByDestination(Map<String, JsonNode> map, String destination) {
        String key = normalise(destination);
        if (map.containsKey(key)) return map.get(key);
        return map.entrySet().stream()
                .filter(e -> e.getKey().contains(key) || key.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<JsonNode> findListByDestination(Map<String, List<JsonNode>> map, String destination) {
        String key = normalise(destination);
        if (map.containsKey(key)) return map.get(key);
        return map.entrySet().stream()
                .filter(e -> e.getKey().contains(key) || key.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

    private String normalise(String s) {
        return s.toLowerCase().trim();
    }
}
