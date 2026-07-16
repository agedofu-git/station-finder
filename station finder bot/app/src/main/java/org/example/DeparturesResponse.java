package org.example;

import java.util.List;

public record DeparturesResponse(
        String stationId,
        String date,
        String timezone,
        List<Departure> departures) {

    public record Departure(
            String routeName,
            String trainType,
            String mode,
            String color,
            String headsign,
            String tripId,
            String stopId,
            long departureSecs,
            boolean headwayBased,
            Integer headwaySecs) {
    }
}
