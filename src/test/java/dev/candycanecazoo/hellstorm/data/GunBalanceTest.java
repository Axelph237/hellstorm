package dev.candycanecazoo.hellstorm.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GunBalanceTest {
    private static GunBalance parse(String json) {
        return GunBalance.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, AssertionError::new);
    }

    @Test
    void anEmptyFileLeavesTheGunAsTaczMadeIt() {
        assertEquals(GunBalance.NONE, parse("{}"));
        assertEquals(7 * 4, GunBalance.NONE.reserve(7, 4));
    }

    @Test
    void theReserveScalesWithTheReserveMagazinesSetting() {
        GunBalance pistol = parse("{\"reserve_multiplier\": 4.25}");
        assertEquals(119, pistol.reserve(7, 4));
        assertEquals(60, pistol.reserve(7, 2), "half the magazines, half the reserve (59.5 rounds up)");
        assertEquals(1.0, pistol.damageMultiplier());
    }

    @Test
    void everyShippedBalanceParsesAndChangesSomething() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of("src/main/resources/data"))) {
            files = walk.filter(p -> p.toString().contains("/hellstorm/gun_balance/") && p.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() >= 50, "expected TacZ's default guns, found " + files.size());
        for (Path file : files) {
            GunBalance balance = parse(Files.readString(file));
            assertNotEquals(GunBalance.NONE, balance, file + " changes nothing");
            assertTrue(balance.damageMultiplier() >= 1.0 && balance.damageMultiplier() <= 1.35, file + " damage " + balance.damageMultiplier());
            assertTrue(balance.reserveMultiplier() >= 0.75, file + " reserve " + balance.reserveMultiplier());
        }
    }
}
