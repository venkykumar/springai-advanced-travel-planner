package com.example.travelplanner.tools;

import com.example.travelplanner.data.LogisticsDataRepository;
import com.example.travelplanner.model.logistics.TransportOption;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class LogisticsTools {

    private final LogisticsDataRepository logisticsRepo;

    public LogisticsTools(LogisticsDataRepository logisticsRepo) {
        this.logisticsRepo = logisticsRepo;
    }

    @Tool(description = "Get round-trip flight cost estimates from the US to a destination for a given number of travelers and travel month")
    public String getFlightEstimates(String destination, int travelers, String month) {
        return logisticsRepo.getFlightEstimate(destination, travelers, month)
                .map(f -> """
                        Flight Estimates for %d traveler(s) to %s in %s:
                        Per person (economy): $%.0f
                        Total round-trip for group: $%.0f
                        Seasonal notes: %s""".formatted(
                        travelers, destination, month,
                        f.perPersonUSD(), f.totalForGroupUSD(), f.seasonalNotes()))
                .orElse("No flight data available for: " + destination);
    }

    @Tool(description = "Get local transportation options at a destination — trains, metro, buses, IC cards, taxis with costs and tips")
    public String getLocalTransportOptions(String destination) {
        var options = logisticsRepo.getTransportOptions(destination);
        if (options.isEmpty()) return "No transport data found for: " + destination;
        return "Local Transport Options for " + destination + ":\n" +
                options.stream()
                        .map(t -> "• %s (%s)\n  Cost: %s\n  Coverage: %s\n  Tip: %s".formatted(
                                t.name(), t.type(), t.costDescription(), t.coverage(), t.tip()))
                        .collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "Get visa and entry requirements for US passport holders visiting a destination")
    public String getVisaRequirements(String destination) {
        return logisticsRepo.getVisaInfo(destination)
                .map(v -> """
                        Visa Requirements for %s (US passport):
                        Visa Required: %s
                        Visa Type: %s
                        Cost: $%.0f
                        Processing Time: %d days
                        Notes: %s""".formatted(
                        destination,
                        v.visaRequired() ? "YES" : "NO",
                        v.visaType(),
                        v.costUSD(),
                        v.processingDays(),
                        v.notes()))
                .orElse("No visa data available for: " + destination);
    }

    @Tool(description = "Estimate airport transfer cost and options for a destination")
    public String getAirportTransferInfo(String destination) {
        var options = logisticsRepo.getTransportOptions(destination);
        // Filter to transport types relevant to airport
        String info = options.stream()
                .filter(t -> t.type().contains("METRO") || t.type().contains("BUS")
                        || t.name().toLowerCase().contains("airport")
                        || t.name().toLowerCase().contains("aerobus")
                        || t.name().toLowerCase().contains("express"))
                .map(TransportOption::costDescription)
                .collect(Collectors.joining("; "));
        return info.isEmpty()
                ? "Taxi or rideshare recommended for airport transfer to " + destination + ". Budget $20-60 depending on distance."
                : "Airport transfer options for " + destination + ": " + info;
    }
}
