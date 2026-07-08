package org.example;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PingListener extends ListenerAdapter {
    private final TransitClient transitClient;

    public PingListener(TransitClient transitClient) {
        this.transitClient = transitClient;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();

        if (message.equals("!ping")) {
            event.getChannel().sendMessage("Pong").queue();
            return;
        }

        if (message.startsWith("!station ")) {
            handleStationCommand(event, message);
            return;
        }
    }

    private void handleStationCommand(
        MessageReceivedEvent event,
        String message
) {
    String stationName = message
            .substring("!station ".length())
            .trim();

    if (stationName.isBlank()) {
        event.getChannel()
                .sendMessage("使い方: `!station 本厚木`")
                .queue();
        return;
    }

    try {
        var stations = transitClient.searchStations(stationName);

        if (stations.isEmpty()) {
            event.getChannel()
                    .sendMessage("駅情報が見つかりませんでした。")
                    .queue();
            return;
        }

        StringBuilder result = new StringBuilder();

        for (station station : stations) {
            result.append("## ")
                    .append(station.name())
                    .append("\n");

            result.append("かな: ")
                    .append(station.nameKana())
                    .append("\n");

            result.append("事業者: ")
                    .append(station.feedName())
                    .append("\n");

            result.append("緯度: ")
                    .append(station.lat())
                    .append("\n");

            result.append("経度: ")
                    .append(station.lon())
                    .append("\n\n");
        }

        event.getChannel()
                .sendMessage(result.toString())
                .queue();

    } catch (Exception e) {
        e.printStackTrace();

        event.getChannel()
                .sendMessage(
                        "検索失敗: `" + e.getMessage() + "`"
                )
                .queue();
    }
}
}