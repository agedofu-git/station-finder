package org.example;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingListenerTest {
    @Test
    void definesAllCommandsAsSlashCommands() {
        List<SlashCommandData> commands = PingListener.commandData();

        assertEquals(List.of("ping", "station", "route", "departures"),
                commands.stream().map(SlashCommandData::getName).toList());
    }

    @Test
    void stationAndRouteOptionsAreRequired() {
        List<SlashCommandData> commands = PingListener.commandData();
        SlashCommandData station = commands.get(1);
        SlashCommandData route = commands.get(2);
        SlashCommandData departures = commands.get(3);

        assertEquals(List.of("name"),
                station.getOptions().stream().map(option -> option.getName()).toList());
        assertTrue(station.getOptions().getFirst().isRequired());
        assertTrue(station.getOptions().getFirst().isAutoComplete());
        assertEquals(List.of(
                        "from", "to", "date", "time", "search",
                        "max_transfers", "rail_only", "avoid_walk"),
                route.getOptions().stream().map(option -> option.getName()).toList());
        assertTrue(route.getOptions().get(0).isRequired());
        assertTrue(route.getOptions().get(1).isRequired());
        assertTrue(route.getOptions().get(0).isAutoComplete());
        assertTrue(route.getOptions().get(1).isAutoComplete());
        assertEquals(List.of("station", "date", "time", "limit"),
                departures.getOptions().stream().map(option -> option.getName()).toList());
        assertTrue(departures.getOptions().getFirst().isAutoComplete());
    }

    @Test
    void parsesAndValidatesDateAndTimeOptions() {
        assertEquals("20260720", PingListener.parseDate("2026-07-20"));
        assertEquals("09:05", PingListener.parseTime("9:05"));
        assertThrows(IllegalArgumentException.class, () -> PingListener.parseDate("2026-02-30"));
        assertThrows(IllegalArgumentException.class, () -> PingListener.parseTime("25:00"));
    }
}
