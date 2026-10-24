package dev.candycanecazoo.hellstorm.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SettingsResolverTest {
    private static final GameSettings SERVER = GameSettings.DEFAULT;

    @Test
    void noOverrideKeepsTheServerSettings() {
        assertEquals(SERVER, SettingsResolver.resolve(SERVER, Optional.empty()).getOrThrow(false, AssertionError::new));
    }

    @Test
    void aPartialOverrideReplacesOnlyWhatItNames() {
        GameSettings resolved = SettingsResolver.resolve(SERVER,
                Optional.of(JsonParser.parseString("{\"rounds\": {\"max_alive\": 40}, \"economy\": {\"kill\": 75}}")))
                .getOrThrow(false, AssertionError::new);
        assertEquals(40, resolved.rounds().maxAlive());
        assertEquals(75, resolved.economy().kill());
        assertEquals(SERVER.rounds().baseCount(), resolved.rounds().baseCount());
        assertEquals(SERVER.spawning(), resolved.spawning());
        assertEquals(SERVER.players(), resolved.players());
    }

    @Test
    void listsAreReplacedNotMerged() {
        GameSettings resolved = SettingsResolver.resolve(SERVER,
                Optional.of(JsonParser.parseString("{\"players\": {\"starting_guns\": [\"tacz:glock_17\"]}}")))
                .getOrThrow(false, AssertionError::new);
        assertEquals(1, resolved.players().startingGuns().size());
        assertEquals("tacz:glock_17", resolved.players().startingGuns().get(0).gunId().toString());
    }

    @Test
    void aBadOverrideIsAnErrorNotASilentDefault() {
        assertTrue(SettingsResolver.resolve(SERVER, Optional.of(JsonParser.parseString("{\"rounds\": {\"max_alive\": \"lots\"}}")))
                .error().isPresent());
    }

    @Test
    void settingsRoundTripThroughJsonAndNbt() {
        var json = GameSettings.CODEC.encodeStart(JsonOps.INSTANCE, SERVER).getOrThrow(false, AssertionError::new);
        assertEquals(SERVER, GameSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, AssertionError::new));
        var nbt = GameSettings.CODEC.encodeStart(NbtOps.INSTANCE, SERVER).getOrThrow(false, AssertionError::new);
        assertEquals(SERVER, GameSettings.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(false, AssertionError::new));
    }
}
