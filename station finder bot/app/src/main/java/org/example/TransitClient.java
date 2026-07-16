package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class TransitClient {
    private static final String DEFAULT_BASE_URL = "https://api.transit.ls8h.com";
    private static final int STATION_API_LIMIT = 10;
    private static final int STATION_CACHE_LIMIT = 100;
    private static final long STATION_CACHE_TTL_NANOS = Duration.ofMinutes(10).toNanos();

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Semaphore apiRequestSlots = new Semaphore(4);
    private final Map<String, CachedStations> stationCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedStations> eldest) {
                    return size() > STATION_CACHE_LIMIT;
                }
            });

    public TransitClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper(),
                DEFAULT_BASE_URL);
    }

    TransitClient(HttpClient client, ObjectMapper objectMapper, String baseUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    public StationSearchResponse searchStations(String stationName)
            throws IOException, InterruptedException {
        return searchStations(stationName, 3);
    }

    public StationSearchResponse suggestStations(String stationName, int limit)
            throws IOException, InterruptedException {
        return searchStations(stationName, limit);
    }

    private StationSearchResponse searchStations(String stationName, int limit)
            throws IOException, InterruptedException {
        requireText(stationName, "駅名");
        if (limit < 1 || limit > STATION_API_LIMIT) {
            throw new IllegalArgumentException("駅候補数は1～" + STATION_API_LIMIT + "件で指定してください。");
        }

        StationSearchResponse.Station selectedStation = findCachedStationById(stationName);
        if (selectedStation != null) {
            return new StationSearchResponse(List.of(selectedStation));
        }

        String query = stripStationSuffix(stationName);
        String cacheKey = normalizeStationName(query).toLowerCase(Locale.ROOT);
        StationSearchResponse response = getCachedStations(cacheKey);

        if (response == null) {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("q", query);
            parameters.put("limit", Integer.toString(STATION_API_LIMIT));

            response = get("/api/v1/locations/suggest", parameters, StationSearchResponse.class);
            cacheStations(cacheKey, response);
        }

        return limitStations(response, limit);
    }

    public RouteSearchResult searchRoute(String originName, String destinationName)
            throws IOException, InterruptedException {
        return searchRoute(originName, destinationName, RouteSearchOptions.defaults());
    }

    public RouteSearchResult searchRoute(
            String originName,
            String destinationName,
            RouteSearchOptions options) throws IOException, InterruptedException {
        requireText(originName, "出発駅");
        requireText(destinationName, "到着駅");
        if (options == null) {
            options = RouteSearchOptions.defaults();
        }

        StationPair stations = resolveStationPair(originName, destinationName);
        StationSearchResponse.Station origin = stations.origin();
        StationSearchResponse.Station destination = stations.destination();

        if (origin.id().equals(destination.id())) {
            throw new IOException("出発駅と到着駅には別の駅を指定してください。");
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("from", origin.id());
        parameters.put("to", destination.id());
        parameters.put("fromLabel", origin.name());
        parameters.put("toLabel", destination.name());
        putIfPresent(parameters, "date", options.date());
        putIfPresent(parameters, "time", options.time());
        parameters.put("type", options.type());
        parameters.put("maxTransfers", Integer.toString(options.maxTransfers()));
        parameters.put("numItineraries", "3");
        if (options.railOnly()) {
            parameters.put("allowModes", "rail,subway,tram");
        }
        if (options.avoidWalk()) {
            parameters.put("avoidWalk", "true");
        }

        RoutePlan plan = get("/api/v1/plan", parameters, RoutePlan.class);
        return new RouteSearchResult(origin, destination, plan);
    }

    public DepartureSearchResult searchDepartures(
            String stationName,
            String date,
            String time,
            int limit) throws IOException, InterruptedException {
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("発車案内の件数は1～20件で指定してください。");
        }

        StationSearchResponse.Station station = resolveStation(stationName);
        Map<String, String> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "date", date);
        putIfPresent(parameters, "time", time);
        parameters.put("limit", Integer.toString(limit));

        String path = "/api/v1/stations/" + encode(station.id()) + "/departures";
        DeparturesResponse board = get(path, parameters, DeparturesResponse.class);
        return new DepartureSearchResult(station, board);
    }

    private StationSearchResponse.Station resolveStation(String stationName)
            throws IOException, InterruptedException {
        return stationCandidates(stationName).getFirst();
    }

    private StationPair resolveStationPair(String originName, String destinationName)
            throws IOException, InterruptedException {
        List<StationSearchResponse.Station> origins = stationCandidates(originName);
        List<StationSearchResponse.Station> destinations = stationCandidates(destinationName);

        for (StationSearchResponse.Station origin : origins) {
            for (StationSearchResponse.Station destination : destinations) {
                if (origin.feedId() != null && origin.feedId().equals(destination.feedId())) {
                    return new StationPair(origin, destination);
                }
            }
        }
        return new StationPair(origins.getFirst(), destinations.getFirst());
    }

    private List<StationSearchResponse.Station> stationCandidates(String stationName)
            throws IOException, InterruptedException {
        StationSearchResponse response = searchStations(stationName, STATION_API_LIMIT);
        List<StationSearchResponse.Station> stations = response.stations();

        if (stations == null || stations.isEmpty()) {
            throw new StationNotFoundException(stationName);
        }

        String normalizedQuery = normalizeStationName(stationName);
        List<StationSearchResponse.Station> exactMatches = stations.stream()
                .filter(station -> station.name() != null)
                .filter(station -> normalizeStationName(station.name()).equalsIgnoreCase(normalizedQuery))
                .toList();
        return exactMatches.isEmpty() ? stations : exactMatches;
    }

    private <T> T get(String path, Map<String, String> parameters, Class<T> responseType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(path, parameters))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "station-finder-discord-bot/1.0")
                .GET()
                .build();

        HttpResponse<String> response;
        apiRequestSlots.acquire();
        try {
            response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } finally {
            apiRequestSlots.release();
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(apiErrorMessage(response.statusCode(), response.body()));
        }

        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException e) {
            throw new IOException("Transit APIのJSONを解析できませんでした。", e);
        }
    }

    private URI buildUri(String path, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return URI.create(baseUrl + path + "?" + query);
    }

    private String apiErrorMessage(int statusCode, String body) {
        String detail = null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root == null ? null : root.path("error");
            if (error == null) {
                return "Transit APIエラー（HTTP " + statusCode + "）";
            }
            if (error.isTextual()) {
                detail = error.asText();
            } else if (error.isObject()) {
                detail = error.path("message").asText(null);
            }
        } catch (JsonProcessingException ignored) {
            // JSON以外のエラーレスポンスではHTTPステータスだけを表示する。
        }

        return detail == null || detail.isBlank()
                ? "Transit APIエラー（HTTP " + statusCode + "）"
                : "Transit APIエラー（HTTP " + statusCode + "）: " + detail;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "を指定してください。");
        }
    }

    private static String normalizeStationName(String name) {
        String normalized = name.trim().replace(" ", "").replace("　", "");
        return normalized.endsWith("駅")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static String stripStationSuffix(String name) {
        String trimmed = name.trim();
        return trimmed.length() > 1 && trimmed.endsWith("駅")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private StationSearchResponse getCachedStations(String cacheKey) {
        synchronized (stationCache) {
            CachedStations cached = stationCache.get(cacheKey);
            if (cached == null) {
                return null;
            }
            if (System.nanoTime() >= cached.expiresAtNanos()) {
                stationCache.remove(cacheKey);
                return null;
            }
            return new StationSearchResponse(cached.stations());
        }
    }

    private StationSearchResponse.Station findCachedStationById(String stationId) {
        long now = System.nanoTime();
        synchronized (stationCache) {
            stationCache.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtNanos());
            return stationCache.values().stream()
                    .flatMap(cached -> cached.stations().stream())
                    .filter(station -> station.id().equals(stationId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void cacheStations(String cacheKey, StationSearchResponse response) {
        List<StationSearchResponse.Station> stations = response.stations() == null
                ? List.of()
                : List.copyOf(response.stations());
        synchronized (stationCache) {
            stationCache.put(cacheKey, new CachedStations(
                    stations,
                    System.nanoTime() + STATION_CACHE_TTL_NANOS));
        }
    }

    private static StationSearchResponse limitStations(StationSearchResponse response, int limit) {
        if (response.stations() == null || response.stations().isEmpty()) {
            return new StationSearchResponse(List.of());
        }
        int end = Math.min(limit, response.stations().size());
        return new StationSearchResponse(List.copyOf(response.stations().subList(0, end)));
    }

    private static void putIfPresent(Map<String, String> parameters, String name, String value) {
        if (value != null && !value.isBlank()) {
            parameters.put(name, value);
        }
    }

    private record CachedStations(
            List<StationSearchResponse.Station> stations,
            long expiresAtNanos) {
    }

    private record StationPair(
            StationSearchResponse.Station origin,
            StationSearchResponse.Station destination) {
    }

    public static class StationNotFoundException extends IOException {
        public StationNotFoundException(String stationName) {
            super("駅が見つかりません: " + stationName);
        }
    }
}
