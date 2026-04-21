package com.example.travelplanner.tools;

import com.example.travelplanner.data.DestinationDataRepository;
import com.example.travelplanner.model.destination.Attraction;
import com.example.travelplanner.model.destination.NeighborhoodGroup;
import com.example.travelplanner.model.destination.SeasonalInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DestinationTools {

    private final DestinationDataRepository destinationRepo;
    private final VectorStore vectorStore;

    public DestinationTools(DestinationDataRepository destinationRepo, VectorStore vectorStore) {
        this.destinationRepo = destinationRepo;
        this.vectorStore = vectorStore;
    }

    @Tool(description = "Get all regions/areas of a destination with their highlights and what they are best for")
    public String getDestinationRegions(String destination) {
        var regions = destinationRepo.getRegions(destination);
        if (regions.isEmpty()) return "No region data found for: " + destination;
        return regions.stream()
                .map(r -> "Region: %s\nDescription: %s\nHighlights: %s\nBest for: %s".formatted(
                        r.name(), r.description(), String.join(", ", r.highlights()), r.bestFor()))
                .collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "Get must-see attractions for a destination with entry fees, duration, and descriptions")
    public String getMustSeeAttractions(String destination) {
        List<Attraction> attractions = destinationRepo.getMustSeeAttractions(destination);
        if (attractions.isEmpty()) return "No attraction data found for: " + destination;
        return attractions.stream()
                .map(a -> "• %s (%s) — %s | Entry: $%.0f | Duration: %d min".formatted(
                        a.name(), a.neighborhood(), a.description(), a.entryFeeUSD(), a.durationMinutes()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Get seasonal travel tips for a destination and specific month — weather, crowds, events, packing advice")
    public String getSeasonalTips(String destination, String month) {
        return destinationRepo.getSeasonalInfo(destination, month)
                .map(s -> """
                        Seasonal Tips for %s in %s:
                        Weather: %s
                        Crowd Level: %s
                        Notable Events: %s
                        Packing Tips: %s
                        Pros: %s
                        Cons: %s""".formatted(
                        destination, s.month(), s.weather(), s.crowdLevel(),
                        String.join(", ", s.notableEvents()),
                        String.join(", ", s.packingTips()),
                        String.join(", ", s.pros()),
                        String.join(", ", s.cons())))
                .orElse("No seasonal data for " + destination + " in " + month);
    }

    @Tool(description = "Get cultural etiquette and practical tips for a destination — customs, tipping, dress code, local norms")
    public String getCulturalTips(String destination) {
        List<String> tips = destinationRepo.getCulturalTips(destination);
        if (tips.isEmpty()) return "No cultural tips found for: " + destination;
        return "Cultural Tips for " + destination + ":\n" +
                tips.stream().map(t -> "• " + t).collect(Collectors.joining("\n"));
    }

    @Tool(description = "Get neighbourhood groupings for efficient day planning — attractions that are close to each other")
    public String getNeighborhoodGroups(String destination) {
        List<NeighborhoodGroup> groups = destinationRepo.getNeighborhoodGroups(destination);
        if (groups.isEmpty()) return "No neighbourhood data found for: " + destination;
        return groups.stream()
                .map(g -> g.name() + ": " + String.join(", ", g.attractions()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Search the travel knowledge base for specific questions about destinations, attractions, or travel tips")
    public String searchKnowledgeBase(String query) {
        var docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(4).build()
        );
        if (docs == null || docs.isEmpty()) return "No relevant information found for: " + query;
        return docs.stream()
                .map(d -> d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
