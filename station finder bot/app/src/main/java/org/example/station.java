package org.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record station(
        String id,
        String name,
        String nameKana,
        String feedId,
        String feedName,
        int score,
        int weight,
        double lat,
        double lon,
        String kind
) {
}