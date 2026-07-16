package org.example;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

public class PingListener extends ListenerAdapter {
    private final TransitClient transitClient;

    public PingListener(TransitClient transitClient) {
        this.transitClient = transitClient;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> event.reply("Pong").queue();
            case "station" -> handleStationCommand(event);
            case "route" -> handleRouteCommand(event);
            case "departures" -> handleDeparturesCommand(event);
            default -> {
                // このリスナーが登録していないコマンドは処理しない。
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!isStationAutoComplete(event)) {
            return;
        }

        String query = event.getFocusedOption().getValue().trim();
        if (query.isEmpty()) {
            event.replyChoices().queue();
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                StationSearchResponse response = transitClient.suggestStations(query, 10);
                List<Command.Choice> choices = response.stations().stream()
                        .filter(station -> hasText(station.name()))
                        .map(PingListener::stationChoice)
                        .toList();
                event.replyChoices(choices).queue();
            } catch (Exception e) {
                System.err.println("駅候補の取得に失敗しました: " + safeMessage(e));
                event.replyChoices().queue();
            }
        });
    }

    static List<SlashCommandData> commandData() {
        return List.of(
                Commands.slash("ping", "Botの応答を確認します"),
                Commands.slash("station", "駅を検索します")
                        .addOptions(stationOption("name", "検索する駅名")),
                Commands.slash("route", "2駅間の乗換経路を検索します")
                        .addOptions(
                                stationOption("from", "出発駅名"),
                                stationOption("to", "到着駅名"),
                                new OptionData(OptionType.STRING, "date", "日付（YYYY-MM-DD）"),
                                new OptionData(OptionType.STRING, "time", "時刻（HH:mm）"),
                                new OptionData(OptionType.STRING, "search", "検索種別")
                                        .addChoice("出発", "departure")
                                        .addChoice("到着", "arrival")
                                        .addChoice("始発", "first")
                                        .addChoice("終電", "last"),
                                new OptionData(OptionType.INTEGER, "max_transfers", "最大乗換回数")
                                        .setMinValue(0)
                                        .setMaxValue(8),
                                new OptionData(OptionType.BOOLEAN, "rail_only", "鉄道のみで検索する"),
                                new OptionData(OptionType.BOOLEAN, "avoid_walk", "徒歩区間を避ける")),
                Commands.slash("departures", "駅の発車案内を表示します")
                        .addOptions(
                                stationOption("station", "発車案内を表示する駅名"),
                                new OptionData(OptionType.STRING, "date", "日付（YYYY-MM-DD）"),
                                new OptionData(OptionType.STRING, "time", "時刻（HH:mm）"),
                                new OptionData(OptionType.INTEGER, "limit", "表示件数")
                                        .setMinValue(1)
                                        .setMaxValue(20)));
    }

    private void handleStationCommand(SlashCommandInteractionEvent event) {
        String stationName = requiredStringOption(event, "name");
        event.deferReply().queue(hook -> Thread.startVirtualThread(() -> {
            try {
                StationSearchResponse response = transitClient.searchStations(stationName);
                hook.editOriginal(TransitMessageFormatter.formatStations(stationName, response))
                        .queue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError(hook, "駅検索が中断されました。");
            } catch (Exception e) {
                e.printStackTrace();
                sendError(hook, "検索失敗: " + safeMessage(e));
            }
        }));
    }

    private void handleRouteCommand(SlashCommandInteractionEvent event) {
        String origin = requiredStringOption(event, "from");
        String destination = requiredStringOption(event, "to");
        RouteSearchOptions options;
        try {
            options = new RouteSearchOptions(
                    parseDate(optionalStringOption(event, "date")),
                    parseTime(optionalStringOption(event, "time")),
                    optionalStringOption(event, "search", "departure"),
                    optionalIntOption(event, "max_transfers", 3),
                    optionalBooleanOption(event, "rail_only"),
                    optionalBooleanOption(event, "avoid_walk"));
        } catch (IllegalArgumentException e) {
            event.reply(e.getMessage()).setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue(hook -> Thread.startVirtualThread(() -> {
            try {
                RouteSearchResult result = transitClient.searchRoute(origin, destination, options);
                hook.editOriginal(TransitMessageFormatter.formatRoute(result))
                        .queue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError(hook, "経路検索が中断されました。");
            } catch (Exception e) {
                e.printStackTrace();
                sendError(hook, "経路検索失敗: " + safeMessage(e));
            }
        }));
    }

    private void handleDeparturesCommand(SlashCommandInteractionEvent event) {
        String station = requiredStringOption(event, "station");
        String date;
        String time;
        try {
            date = parseDate(optionalStringOption(event, "date"));
            time = parseTime(optionalStringOption(event, "time"));
        } catch (IllegalArgumentException e) {
            event.reply(e.getMessage()).setEphemeral(true).queue();
            return;
        }
        int limit = optionalIntOption(event, "limit", 10);

        event.deferReply().queue(hook -> Thread.startVirtualThread(() -> {
            try {
                DepartureSearchResult result = transitClient.searchDepartures(station, date, time, limit);
                hook.editOriginal(TransitMessageFormatter.formatDepartures(result)).queue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError(hook, "発車案内の検索が中断されました。");
            } catch (Exception e) {
                e.printStackTrace();
                sendError(hook, "発車案内の取得失敗: " + safeMessage(e));
            }
        }));
    }

    private static OptionData stationOption(String name, String description) {
        return new OptionData(OptionType.STRING, name, description, true)
                .setAutoComplete(true);
    }

    private static boolean isStationAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String command = event.getName();
        String option = event.getFocusedOption().getName();
        return ("station".equals(command) && "name".equals(option))
                || ("route".equals(command) && ("from".equals(option) || "to".equals(option)))
                || ("departures".equals(command) && "station".equals(option));
    }

    private static Command.Choice stationChoice(StationSearchResponse.Station station) {
        String label = station.name();
        if (hasText(station.feedName())) {
            label += "（" + station.feedName() + "）";
        }
        if (label.length() > 100) {
            label = label.substring(0, 100);
        }

        String value = hasText(station.id()) && station.id().length() <= 100
                ? station.id()
                : station.name();
        return new Command.Choice(label, value);
    }

    private static String requiredStringOption(SlashCommandInteractionEvent event, String name) {
        return Objects.requireNonNull(event.getOption(name), "必須オプションがありません: " + name)
                .getAsString()
                .trim();
    }

    private static String optionalStringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? null : option.getAsString().trim();
    }

    private static String optionalStringOption(
            SlashCommandInteractionEvent event,
            String name,
            String defaultValue) {
        String value = optionalStringOption(event, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int optionalIntOption(SlashCommandInteractionEvent event, String name, int defaultValue) {
        OptionMapping option = event.getOption(name);
        return option == null ? defaultValue : Math.toIntExact(option.getAsLong());
    }

    private static boolean optionalBooleanOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option != null && option.getAsBoolean();
    }

    static String parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日付はYYYY-MM-DD形式で指定してください。");
        }
    }

    static String parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("\\d{1,2}:\\d{2}")) {
            throw new IllegalArgumentException("時刻はHH:mm形式で指定してください。");
        }
        String[] parts = value.split(":", 2);
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("時刻は00:00～23:59で指定してください。");
        }
        return "%02d:%02d".formatted(hour, minute);
    }

    private static void sendError(InteractionHook hook, String message) {
        hook.editOriginal(message).queue();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replace('`', '\'');
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
