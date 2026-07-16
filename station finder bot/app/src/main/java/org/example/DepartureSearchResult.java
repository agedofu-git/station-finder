package org.example;

public record DepartureSearchResult(
        StationSearchResponse.Station station,
        DeparturesResponse board) {
}
