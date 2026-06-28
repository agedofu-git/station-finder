package org.example;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class App {
    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");

        JDABuilder.createLight(
                token,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        )
        .addEventListeners(new BotListener())
        .build();
    }
}