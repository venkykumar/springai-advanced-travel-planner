package com.example.travelplanner.data;

import com.example.travelplanner.model.destination.Attraction;
import com.example.travelplanner.model.destination.NeighborhoodGroup;
import com.example.travelplanner.model.destination.Region;
import com.example.travelplanner.model.destination.SeasonalInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class DestinationDataRepository {

    private static final Logger log = LoggerFactory.getLogger(DestinationDataRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, JsonNode> destinationData = new HashMap<>();

    private static final List<String> DATA_FILES = List.of(
            "classpath:data/destinations/japan.json",
            "classpath:data/destinations/france-paris.json",
            "classpath:data/destinations/italy.json",
            "classpath:data/destinations/thailand.json",
            "classpath:data/destinations/spain-barcelona.json"
    );

    @PostConstruct
    public void load() {
        var resolver = new PathMatchingResourcePatternResolver();
        for (String path : DATA_FILES) {
            try {
                Resource resource = resolver.getResource(path);
                JsonNode node = objectMapper.readTree(resource.getInputStream());
                String dest = node.get("destination").asText();
                destinationData.put(normalise(dest), node);
                log.info("Loaded destination data: {}", dest);
            } catch (Exception e) {
                log.error("Failed to load destination file {}", path, e);
            }
        }
    }

    public List<String> supportedDestinations() {
        return destinationData.values().stream()
                .map(n -> n.get("destination").asText())
                .sorted()
                .toList();
    }

    public Optional<JsonNode> getRawData(String destination) {
        return Optional.ofNullable(destinationData.get(normalise(destination)));
    }

    public List<Region> getRegions(String destination) {
        return getRawData(destination).map(node -> {
            List<Region> regions = new ArrayList<>();
            node.get("regions").forEach(r -> regions.add(new Region(
                    r.get("name").asText(),
                    r.get("description").asText(),
                    toStringList(r.get("highlights")),
                    r.get("bestFor").asText()
            )));
            return regions;
        }).orElse(List.of());
    }

    public List<Attraction> getAttractions(String destination) {
        return getRawData(destination).map(node -> {
            List<Attraction> list = new ArrayList<>();
            node.get("attractions").forEach(a -> list.add(new Attraction(
                    a.get("name").asText(),
                    a.get("description").asText(),
                    a.get("neighborhood").asText(),
                    a.get("region").asText(),
                    a.get("entryFeeUSD").asDouble(),
                    a.get("durationMinutes").asInt(),
                    a.get("category").asText(),
                    a.get("mustSee").asBoolean()
            )));
            return list;
        }).orElse(List.of());
    }

    public List<Attraction> getMustSeeAttractions(String destination) {
        return getAttractions(destination).stream()
                .filter(Attraction::mustSee)
                .toList();
    }

    public Optional<SeasonalInfo> getSeasonalInfo(String destination, String month) {
        return getRawData(destination).map(node -> {
            JsonNode seasonal = node.get("seasonalTips");
            if (seasonal == null) return null;
            JsonNode m = seasonal.get(month.toLowerCase());
            if (m == null) return null;
            return new SeasonalInfo(
                    m.get("month").asText(),
                    m.get("weather").asText(),
                    m.get("crowdLevel").asText(),
                    toStringList(m.get("notableEvents")),
                    toStringList(m.get("packingTips")),
                    toStringList(m.get("pros")),
                    toStringList(m.get("cons"))
            );
        });
    }

    public List<String> getCulturalTips(String destination) {
        return getRawData(destination)
                .map(node -> toStringList(node.get("culturalTips")))
                .orElse(List.of());
    }

    public List<NeighborhoodGroup> getNeighborhoodGroups(String destination) {
        return getRawData(destination).map(node -> {
            List<NeighborhoodGroup> groups = new ArrayList<>();
            JsonNode ng = node.get("neighborhoodGroups");
            if (ng != null) {
                ng.fields().forEachRemaining(entry ->
                        groups.add(new NeighborhoodGroup(entry.getKey(), toStringList(entry.getValue())))
                );
            }
            return groups;
        }).orElse(List.of());
    }

    // Returns a JSON string describing all destination data — used for knowledge base ingestion
    public String getFullTextDescription(String destination) {
        return getRawData(destination).map(node -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Destination: ").append(node.get("destination").asText()).append("\n\n");
            sb.append("Regions:\n");
            node.get("regions").forEach(r ->
                    sb.append("- ").append(r.get("name").asText()).append(": ").append(r.get("description").asText()).append("\n")
            );
            sb.append("\nTop Attractions:\n");
            node.get("attractions").forEach(a ->
                    sb.append("- ").append(a.get("name").asText())
                            .append(" (").append(a.get("neighborhood").asText()).append("): ")
                            .append(a.get("description").asText())
                            .append(" Entry: $").append(a.get("entryFeeUSD").asDouble())
                            .append(", Duration: ").append(a.get("durationMinutes").asInt()).append(" min\n")
            );
            sb.append("\nCultural Tips:\n");
            node.get("culturalTips").forEach(t -> sb.append("- ").append(t.asText()).append("\n"));
            return sb.toString();
        }).orElse("No data available for " + destination);
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(n -> result.add(n.asText()));
        return result;
    }

    private String normalise(String destination) {
        return destination.toLowerCase().trim();
    }
}
