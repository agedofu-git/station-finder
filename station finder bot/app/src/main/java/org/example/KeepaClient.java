package org.example;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class KeepaClient {
    private final String apiKey;
    private final HttpClient httpClient;

    public KeepaClient() {
        this.apiKey = System.getenv("KEEPA_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("KEEPA_API_KEY が設定されていません");
        }

        this.httpClient = HttpClient.newHttpClient();
    }

    public String fetchProductJson(String asin) throws Exception {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String encodedAsin = URLEncoder.encode(asin, StandardCharsets.UTF_8);

        String url = "https://api.keepa.com/product"
                + "?key=" + encodedKey
                + "&domain=5"
                + "&asin=" + encodedAsin;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Keepa API エラー: " + response.statusCode());
        }

        return response.body();
    }
}