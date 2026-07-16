package org.example;

import java.util.List;

public record StationSearchResponse(List<Station> stations) {
    public record Station(
            String id,
            String name,
            String nameKana,
            String feedId,
            String feedName,
            Double score,
            Double weight,
            Double lat,
            Double lon,
            String kind) {
    }
}
