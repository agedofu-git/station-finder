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

    private void handleStationCommand(MessageReceivedEvent event, String message) {
        String stationName = message.substring("!station ".length()).trim();

        if (stationName.isBlank()) {
            event.getChannel().sendMessage("使い方: `!station 本厚木`").queue();
            return;
        }

        try {
            String result = transitClient.searchStationRaw(stationName);

            if (result == null || result.isBlank()) {
                event.getChannel().sendMessage("駅情報が見つかりませんでした。").queue();
                return;
            }

            if (result.length() > 1800) {
                result = result.substring(0, 1800) + "...";
            }

            event.getChannel().sendMessage("```json\n" + result + "\n```").queue();

        } catch (Exception e) {
            e.printStackTrace();
            event.getChannel().sendMessage("検索失敗: `" + e.getMessage() + "`").queue();
        }
    }
}