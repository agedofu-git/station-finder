package org.example;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TransitMessageFormatter {
    private static final int DISCORD_SAFE_LENGTH = 1900;

    private TransitMessageFormatter() {
    }

    public static String formatStations(String query, StationSearchResponse response) {
        List<StationSearchResponse.Station> stations = response.stations();
        if (stations == null || stations.isEmpty()) {
            return "「" + query + "」に一致する駅は見つかりませんでした。";
        }

        String heading = stations.size() == 1 && hasText(stations.getFirst().name())
                ? stations.getFirst().name()
                : query;
        StringBuilder result = new StringBuilder("**駅検索: ")
                .append(heading)
                .append("**\n");

        for (int i = 0; i < stations.size(); i++) {
            StationSearchResponse.Station station = stations.get(i);
            result.append(i + 1).append(". **").append(station.name()).append("**");
            if (hasText(station.nameKana())) {
                result.append("（").append(station.nameKana()).append("）");
            }
            if (hasText(station.feedName())) {
                result.append("\n   ").append(station.feedName());
            }
            result.append('\n');
        }

        return truncate(result.toString().stripTrailing());
    }

    public static String formatRoute(RouteSearchResult result) {
        RoutePlan plan = result.plan();
        List<RoutePlan.Journey> journeys = plan.journeys();
        String title = "**" + result.origin().name() + " → " + result.destination().name() + "**";

        if (journeys == null || journeys.isEmpty()) {
            return title + "\n利用できる経路が見つかりませんでした。";
        }

        StringBuilder message = new StringBuilder(title);
        if (hasText(plan.date()) && plan.date().length() == 8) {
            message.append("\n検索日: ")
                    .append(plan.date(), 0, 4).append('/')
                    .append(plan.date(), 4, 6).append('/')
                    .append(plan.date(), 6, 8);
        }

        int displayed = Math.min(3, journeys.size());
        for (int i = 0; i < displayed; i++) {
            appendJourney(message, journeys.get(i), i + 1);
        }

        return truncate(message.toString());
    }

    public static String formatDepartures(DepartureSearchResult result) {
        StationSearchResponse.Station station = result.station();
        DeparturesResponse board = result.board();
        StringBuilder message = new StringBuilder("**")
                .append(station.name())
                .append(" 発車案内**");
        if (hasText(station.feedName())) {
            message.append("\n路線: ").append(station.feedName());
        }
        if (hasText(board.date()) && board.date().length() == 8) {
            message.append("\n検索日: ").append(formatDate(board.date()));
        }

        List<DeparturesResponse.Departure> departures = board.departures();
        if (departures == null || departures.isEmpty()) {
            return message.append("\n指定時刻以降の発車情報が見つかりませんでした。")
                    .toString();
        }

        for (int i = 0; i < departures.size(); i++) {
            DeparturesResponse.Departure departure = departures.get(i);
            message.append("\n")
                    .append(i + 1).append(". **")
                    .append(formatTime(departure.departureSecs()))
                    .append("** ")
                    .append(departure.routeName());

            List<String> details = new ArrayList<>();
            if (hasText(departure.trainType())) {
                details.add(departure.trainType());
            }
            if (hasText(departure.headsign())) {
                details.add(departure.headsign());
            }
            if (departure.headwayBased() && departure.headwaySecs() != null) {
                details.add("約" + formatDuration(departure.headwaySecs()) + "間隔");
            }
            if (!details.isEmpty()) {
                message.append("\n   ").append(String.join(" / ", details));
            }
        }

        return truncate(message.toString());
    }

    private static void appendJourney(StringBuilder message, RoutePlan.Journey journey, int number) {
        message.append("\n\n**経路").append(number).append("**  ")
                .append(formatTime(journey.departureSecs()))
                .append(" → ")
                .append(formatTime(journey.arrivalSecs()))
                .append("（").append(formatDuration(journey.durationSecs())).append("）")
                .append(" / 乗換").append(journey.transferCount()).append("回");

        appendFare(message, journey.fare());

        if (journey.transferCount() > 0) {
            List<String> transfers = transferStations(journey.legs());
            if (!transfers.isEmpty()) {
                message.append("\n乗換駅: ").append(String.join("、", transfers));
            }
        }

        if (journey.legs() == null) {
            return;
        }

        for (RoutePlan.Leg leg : journey.legs()) {
            message.append("\n・").append(formatLeg(leg));
        }
    }

    private static void appendFare(StringBuilder message, RoutePlan.Fare fare) {
        if (fare == null || (fare.ticket() == null && fare.ic() == null)) {
            return;
        }

        message.append("\n運賃: ");
        if (fare.ic() != null) {
            message.append("IC ").append(formatMoney(fare.ic(), fare.currency()));
        }
        if (fare.ticket() != null) {
            if (fare.ic() != null) {
                message.append(" / ");
            }
            message.append("きっぷ ").append(formatMoney(fare.ticket(), fare.currency()));
        }
    }

    private static String formatLeg(RoutePlan.Leg leg) {
        String from = formatStop(leg.from());
        String to = formatStop(leg.to());
        long duration = Math.max(0, leg.arrivalSecs() - leg.departureSecs());

        if ("walk".equals(leg.kind())) {
            return "徒歩 " + from + " → " + to + "（" + formatDuration(duration) + "）";
        }

        StringBuilder line = new StringBuilder()
                .append(formatTime(leg.departureSecs())).append(' ')
                .append(from).append(" → ")
                .append(formatTime(leg.arrivalSecs())).append(' ')
                .append(to);

        List<String> details = new ArrayList<>();
        if (hasText(leg.routeName())) {
            details.add(leg.routeName());
        }
        if (hasText(leg.trainType())) {
            details.add(leg.trainType());
        }
        if (hasText(leg.headsign())) {
            details.add(leg.headsign() + "方面");
        }
        if (!details.isEmpty()) {
            line.append("\n  ").append(String.join(" / ", details));
        }
        return line.toString();
    }

    private static List<String> transferStations(List<RoutePlan.Leg> legs) {
        if (legs == null) {
            return List.of();
        }

        Set<String> transfers = new LinkedHashSet<>();
        boolean hasSeenTransit = false;
        for (RoutePlan.Leg leg : legs) {
            if (!"transit".equals(leg.kind())) {
                continue;
            }
            if (hasSeenTransit && leg.from() != null && hasText(leg.from().name())) {
                transfers.add(leg.from().name());
            }
            hasSeenTransit = true;
        }
        return transfers.stream().collect(Collectors.toList());
    }

    static String formatTime(long seconds) {
        String sign = seconds < 0 ? "-" : "";
        long absolute = Math.abs(seconds);
        long hours = absolute / 3600;
        long minutes = (absolute % 3600) / 60;
        return "%s%02d:%02d".formatted(sign, hours, minutes);
    }

    static String formatDuration(long seconds) {
        long totalMinutes = Math.max(1, (Math.max(0, seconds) + 59) / 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + "分";
        }
        if (minutes == 0) {
            return hours + "時間";
        }
        return hours + "時間" + minutes + "分";
    }

    private static String formatStop(RoutePlan.Stop stop) {
        if (stop == null) {
            return "不明";
        }
        if (hasText(stop.platformCode())) {
            return stop.name() + "（" + stop.platformCode() + "番線）";
        }
        return stop.name();
    }

    private static String formatMoney(int amount, String currency) {
        return "JPY".equalsIgnoreCase(currency) || !hasText(currency)
                ? amount + "円"
                : amount + " " + currency;
    }

    private static String formatDate(String basicDate) {
        return basicDate.substring(0, 4) + "/"
                + basicDate.substring(4, 6) + "/"
                + basicDate.substring(6, 8);
    }

    private static String truncate(String message) {
        return message.length() <= DISCORD_SAFE_LENGTH
                ? message
                : message.substring(0, DISCORD_SAFE_LENGTH - 4) + "\n...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
