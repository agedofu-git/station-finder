package org.example;

public record RouteSearchResult(
        StationSearchResponse.Station origin,
        StationSearchResponse.Station destination,
        RoutePlan plan) {
}
