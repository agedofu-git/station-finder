package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitClientTest {
    private HttpServer server;
    private TransitClient client;
    private final AtomicInteger stationSearchRequests = new AtomicInteger();
    private volatile Map<String, String> lastPlanParameters;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/locations/suggest", this::handleStationSearch);
        server.createContext("/api/v1/plan", this::handleRoutePlan);
        server.createContext("/api/v1/stations/", this::handleDepartures);
        server.start();

        client = new TransitClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void searchStationsDeserializesJsonWithJackson() throws Exception {
        StationSearchResponse response = client.searchStations("東京");

        assertEquals(2, response.stations().size());
        assertEquals("東京", response.stations().getFirst().name());
        assertEquals("とうきょう", response.stations().getFirst().nameKana());
    }

    @Test
    void searchStationsAcceptsJapaneseStationSuffix() throws Exception {
        StationSearchResponse response = client.searchStations("東京駅");

        assertEquals("東京", response.stations().getFirst().name());
    }

    @Test
    void stationSearchUsesTheSmallInMemoryCache() throws Exception {
        client.searchStations("東京");
        client.searchStations("東京");

        assertEquals(1, stationSearchRequests.get());
    }

    @Test
    void searchRouteResolvesNamesAndDeserializesJourney() throws Exception {
        RouteSearchResult result = client.searchRoute("東京", "新宿");

        assertEquals("東京", result.origin().name());
        assertEquals("新宿", result.destination().name());
        assertEquals("中央線", result.origin().feedName());
        assertEquals(1, result.plan().journeys().size());
        assertEquals("中央線", result.plan().journeys().getFirst().legs().getFirst().routeName());
        assertEquals(0, result.plan().journeys().getFirst().transferCount());
    }

    @Test
    void searchRouteSendsDateTimeAndLightweightFilters() throws Exception {
        RouteSearchOptions options = new RouteSearchOptions(
                "20260720", "09:15", "arrival", 1, true, true);

        client.searchRoute("東京", "新宿", options);

        assertEquals("20260720", lastPlanParameters.get("date"));
        assertEquals("09:15", lastPlanParameters.get("time"));
        assertEquals("arrival", lastPlanParameters.get("type"));
        assertEquals("1", lastPlanParameters.get("maxTransfers"));
        assertEquals("rail,subway,tram", lastPlanParameters.get("allowModes"));
        assertEquals("true", lastPlanParameters.get("avoidWalk"));
    }

    @Test
    void searchDeparturesDeserializesAndFormatsTheBoard() throws Exception {
        String selectedStationId = client.suggestStations("東京", 10)
                .stations().getFirst().id();
        DepartureSearchResult result = client.searchDepartures(
                selectedStationId, "20260720", "09:00", 5);

        assertEquals("東京", result.station().name());
        assertEquals(1, stationSearchRequests.get());
        assertEquals(1, result.board().departures().size());
        assertEquals("中央線", result.board().departures().getFirst().routeName());
        String message = TransitMessageFormatter.formatDepartures(result);
        assertTrue(message.contains("東京 発車案内"));
        assertTrue(message.contains("09:05"));
        assertTrue(message.contains("快速 高尾"));
    }

    @Test
    void formatterIncludesRouteAndTransferDetails() throws Exception {
        String message = TransitMessageFormatter.formatRoute(client.searchRoute("東京", "新宿"));

        assertTrue(message.contains("東京 → 新宿"));
        assertTrue(message.contains("08:00 → 08:30"));
        assertTrue(message.contains("乗換0回"));
        assertTrue(message.contains("中央線"));
        assertTrue(message.contains("高尾方面"));
    }

    @Test
    void formatterListsTheTransferStation() {
        RoutePlan.Stop tokyo = new RoutePlan.Stop("feed:tokyo", "東京", null);
        RoutePlan.Stop kanda = new RoutePlan.Stop("feed:kanda", "神田", null);
        RoutePlan.Stop ueno = new RoutePlan.Stop("feed:ueno", "上野", null);
        RoutePlan.Leg firstLeg = new RoutePlan.Leg(
                "transit", "中央線", "快速", "rail", null, "高尾", "trip:1",
                tokyo, kanda, 28_800, 29_100, false);
        RoutePlan.Leg secondLeg = new RoutePlan.Leg(
                "transit", "山手線", null, "rail", null, "上野", "trip:2",
                kanda, ueno, 29_220, 29_580, false);
        RoutePlan.Journey journey = new RoutePlan.Journey(
                28_800, 29_580, 780, 1, null, null, null, List.of(firstLeg, secondLeg));
        RoutePlan plan = new RoutePlan(
                "20260717", "departure", "Asia/Tokyo", tokyo, ueno, List.of(journey));
        RouteSearchResult result = new RouteSearchResult(
                station("feed:tokyo", "東京"), station("feed:ueno", "上野"), plan);

        String message = TransitMessageFormatter.formatRoute(result);

        assertTrue(message.contains("乗換1回"));
        assertTrue(message.contains("乗換駅: 神田"));
        assertTrue(message.contains("中央線"));
        assertTrue(message.contains("山手線"));
    }

    private void handleStationSearch(HttpExchange exchange) throws IOException {
        stationSearchRequests.incrementAndGet();
        String query = queryParameters(exchange).get("q");
        String body = "東京".equals(query)
                ? """
                  {"stations":[
                    {"id":"feed:tokyo-shinkansen","name":"東京","nameKana":"とうきょう",
                     "feedId":"feed:shinkansen","feedName":"新幹線","score":3,"weight":100,
                     "lat":35.0,"lon":139.0,"kind":"station"},
                    {"id":"feed:tokyo-chuo","name":"東京","nameKana":"とうきょう",
                     "feedId":"feed:chuo","feedName":"中央線","score":3,"weight":90,
                     "lat":35.0,"lon":139.0,"kind":"station","futureField":"ignored"}
                  ]}
                  """
                : """
                  {"stations":[{
                    "id":"feed:shinjuku-chuo","name":"新宿","nameKana":"しんじゅく",
                    "feedId":"feed:chuo","feedName":"中央線","score":3,"weight":100,
                    "lat":35.0,"lon":139.0,"kind":"station"
                  }]}
                  """;
        sendJson(exchange, 200, body);
    }

    private void handleRoutePlan(HttpExchange exchange) throws IOException {
        Map<String, String> parameters = queryParameters(exchange);
        lastPlanParameters = parameters;
        assertEquals("feed:tokyo-chuo", parameters.get("from"));
        assertEquals("feed:shinjuku-chuo", parameters.get("to"));
        String body = """
                {
                  "date":"20260717","type":"departure","timezone":"Asia/Tokyo",
                  "from":{"id":"feed:tokyo","name":"東京"},
                  "to":{"id":"feed:shinjuku","name":"新宿"},
                  "journeys":[{
                    "departureSecs":28800,"arrivalSecs":30600,"durationSecs":1800,
                    "transferCount":0,"fare":{"currency":"JPY","ticket":210,"ic":208},
                    "legs":[{
                      "kind":"transit","routeName":"中央線","trainType":"快速",
                      "mode":"rail","headsign":"高尾","tripId":"trip:1",
                      "from":{"id":"feed:tokyo","name":"東京","platformCode":"1"},
                      "to":{"id":"feed:shinjuku","name":"新宿","platformCode":"2"},
                      "departureSecs":28800,"arrivalSecs":30600,"headwayBased":false
                    }]
                  }]
                }
                """;
        sendJson(exchange, 200, body);
    }

    private void handleDepartures(HttpExchange exchange) throws IOException {
        Map<String, String> parameters = queryParameters(exchange);
        assertEquals("20260720", parameters.get("date"));
        assertEquals("09:00", parameters.get("time"));
        assertEquals("5", parameters.get("limit"));
        String body = """
                {
                  "stationId":"feed:tokyo","date":"20260720","timezone":"Asia/Tokyo",
                  "departures":[{
                    "routeName":"中央線","trainType":"快速","mode":"rail",
                    "color":"f15a22","headsign":"快速 高尾","tripId":"trip:1",
                    "stopId":"feed:tokyo","departureSecs":32700,
                    "headwayBased":false
                  }]
                }
                """;
        sendJson(exchange, 200, body);
    }

    private static StationSearchResponse.Station station(String id, String name) {
        return new StationSearchResponse.Station(
                id, name, null, "feed", null, null, null, null, null, "station");
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) {
        return Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> parts.length == 2 ? decode(parts[1]) : ""));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
