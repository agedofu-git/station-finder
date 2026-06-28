package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class TransitClient {
    private final HttpClient client = HttpClient.newHttpClient();

    public String searchStationRaw(String stationName) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(stationName, StandardCharsets.UTF_8);

        String url = "https://api.transit.ls8h.com/api/v1/locations/suggest"
                + "?q=" + encoded
                + "&limit=3";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "APIエラー: " + response.statusCode() + "\n" + response.body();
        }

        return response.body();
    }
}