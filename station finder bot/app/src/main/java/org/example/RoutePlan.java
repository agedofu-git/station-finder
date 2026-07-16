package org.example;

import java.util.List;

public record RoutePlan(
        String date,
        String type,
        String timezone,
        Stop from,
        Stop to,
        List<Journey> journeys) {

    public record Stop(String id, String name, String platformCode) {
    }

    public record Journey(
            long departureSecs,
            long arrivalSecs,
            long durationSecs,
            int transferCount,
            Fare fare,
            Integer accessWalkSecs,
            Integer egressWalkSecs,
            List<Leg> legs) {
    }

    public record Fare(String currency, Integer ticket, Integer ic) {
    }

    public record Leg(
            String kind,
            String routeName,
            String trainType,
            String mode,
            String color,
            String headsign,
            String tripId,
            Stop from,
            Stop to,
            long departureSecs,
            long arrivalSecs,
            Boolean headwayBased) {
    }
}
