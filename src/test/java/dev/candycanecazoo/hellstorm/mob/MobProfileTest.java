package dev.candycanecazoo.hellstorm.mob;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MobProfileTest {
    private static MobProfile parse(String json) {
        return MobProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, AssertionError::new);
    }

    @Test
    void onlyTheEntityIsRequired() {
        MobProfile profile = parse("{\"entity\": \"minecraft:skeleton\"}");
        assertEquals("minecraft:skeleton", profile.entity().toString());
        assertEquals(10, profile.weight());
        assertEquals(MobProfile.Ai.REPLACE, profile.ai());
        assertEquals(1, profile.minRound());
        assertEquals(Optional.empty(), profile.maxRound());
        assertTrue(profile.breaksBarricades());
        assertEquals(MobProfile.Navigation.FLOW, profile.navigation());
    }

    @Test
    void everyFieldParses() {
        MobProfile profile = parse("""
                {"entity": "mymod:ghoul", "weight": 3, "min_round": 5, "max_round": 9, "ai": "augment",
                 "nbt": "{CustomName:'Ghoul'}", "health_multiplier": 2.5, "speed_multiplier": 1.2,
                 "damage": 7, "breaks_barricades": false, "navigation": "vanilla"}
                """);
        assertEquals(MobProfile.Ai.AUGMENT, profile.ai());
        assertEquals(Optional.of(9), profile.maxRound());
        assertEquals(2.5, profile.healthMultiplier());
        assertFalse(profile.breaksBarricades());
        assertEquals(MobProfile.Navigation.VANILLA, profile.navigation());
        assertTrue(profile.nbt().orElseThrow().contains("Ghoul"));
        assertFalse(profile.allowedIn(4));
        assertTrue(profile.allowedIn(9));
        assertFalse(profile.allowedIn(10));
    }

    @Test
    void badValuesAreErrors() {
        assertTrue(MobProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entity\": \"minecraft:zombie\", \"ai\": \"chaos\"}")).error().isPresent());
        assertTrue(MobProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 3}")).error().isPresent());
    }

    private static boolean refusesNbt(String snbt) {
        String json = "{\"entity\": \"minecraft:zombie\", \"nbt\": " + new com.google.gson.JsonPrimitive(snbt) + "}";
        return MobProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent();
    }

    @Test
    void nbtThatDoesntParseOrStopsACrawlerIsRefused() {
        assertTrue(refusesNbt("{ForgeData:{"), "SNBT that doesn't parse");
        assertTrue(refusesNbt("{ForgeData:{hellstorm_crawler:{speed_multiplier:0f}}}"), "a crawler that never moves");
        assertTrue(refusesNbt("{ForgeData:{hellstorm_crawler:{speed_multiplier:-0.5f}}}"), "a negative crawl speed");
        assertTrue(refusesNbt("{ForgeData:{hellstorm_crawler:{speed_multiplier:'fast'}}}"), "a crawl speed that isn't a number");
        assertFalse(refusesNbt("{ForgeData:{hellstorm_crawler:{speed_multiplier:0.8f}}}"), "the shipped crawler");
        assertFalse(refusesNbt("{ForgeData:{hellstorm_crawler:{}}}"), "a crawler without a speed uses the usual one");
        assertFalse(refusesNbt("{CustomName:'\"Ghoul\"'}"), "other NBT");
    }
}
