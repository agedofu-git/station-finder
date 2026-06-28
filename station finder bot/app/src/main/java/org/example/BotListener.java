package org.example;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class BotListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Bot自身の発言には反応しない
        if (event.getAuthor().isBot()) {
            return;
        }

        // Discordに送られたメッセージ本文を取得
        String message = event.getMessage().getContentRaw();

        // !ping と完全一致したら Pong! と返す
        if (message.equals("!ping")) {
            event.getChannel().sendMessage("Pong!").queue();
            return;
        }

        // AmazonっぽいURLが含まれていたら反応する
        if (containsAmazonUrl(message)) {
            String asin = extractAsin(message);

            if (asin != null) {
                try {
                    KeepaClient keepaClient = new KeepaClient();
                    String json = keepaClient.fetchProductJson(asin);

                    event.getChannel()
                            .sendMessage("Keepaから取得できたよ\n```json\n"
                                    + json.substring(0, Math.min(json.length(), 1000))
                                    + "\n```")
                            .queue();

                } catch (Exception e) {
                    event.getChannel()
                            .sendMessage("Keepa取得でエラー: " + e.getMessage())
                            .queue();
                }
            } else {
                event.getChannel().sendMessage("ASINが見つかりませんでした").queue();
            }

            return;
        }

    }

    private boolean containsAmazonUrl(String text) {
        return text.contains("amazon.co.jp")
                || text.contains("amazon.com")
                || text.contains("amzn.asia")
                || text.contains("amzn.to");
    }

    private String extractAsin(String text) {
        String[] patterns = {
                "/dp/([A-Z0-9]{10})",
                "/gp/product/([A-Z0-9]{10})",
                "/product/([A-Z0-9]{10})"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);

            if (m.find()) {
                return m.group(1);
            }
        }

        return null;
    }
}