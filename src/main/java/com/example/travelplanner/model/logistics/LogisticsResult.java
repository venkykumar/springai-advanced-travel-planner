package com.example.travelplanner.model.logistics;

import java.util.List;

public record LogisticsResult(
        String destination,
        FlightEstimate flightEstimate,
        List<TransportOption> transportOptions,
        VisaInfo visaInfo,
        List<String> logisticsTips
) {}
