package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class App {
    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN がない");
        }

        TransitClient transitClient = new TransitClient();

        JDA jda = JDABuilder.createLight(token)
                .addEventListeners(new PingListener(transitClient))
                .build();

        jda.updateCommands()
                .addCommands(PingListener.commandData())
                .queue(
                        commands -> System.out.println("スラッシュコマンドを登録しました。"),
                        error -> System.err.println("スラッシュコマンドの登録に失敗しました: "
                                + error.getMessage()));
    }
}
