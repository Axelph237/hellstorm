package dev.candycanecazoo.hellstorm.powerup;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpDropsTest {
    private static final ResourceLocation FISSION = ResourceLocation.tryBuild("hellstorm", "fission");
    private static final ResourceLocation MAX_AMMO = ResourceLocation.tryBuild("hellstorm", "max_ammo");
    private static final PowerUpDrops.Table TABLE = new PowerUpDrops.Table(List.of(new PowerUpDrops.Entry(FISSION, 1), new PowerUpDrops.Entry(MAX_AMMO, 3)));

    private static GameSettings.PowerUps chance(double chance, int cap) {
        return new GameSettings.PowerUps(chance, cap, 600, 100, 400, 200, Integer.MAX_VALUE, 1.0);
    }

    @Test
    void nothingDropsOnceTheRoundsCapIsReached() {
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 100; i++) {
            assertEquals(Optional.empty(), PowerUpDrops.roll(chance(1.0, 4), 4, false, TABLE, random));
        }
        assertTrue(PowerUpDrops.roll(chance(1.0, 4), 3, false, TABLE, random).isPresent(), "the fourth drop of the round is allowed");
    }

    @Test
    void theChanceAndTheWeightsHoldOverManyKills() {
        RandomSource random = RandomSource.create(42);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        int kills = 20_000;
        for (int i = 0; i < kills; i++) {
            PowerUpDrops.roll(chance(0.1, Integer.MAX_VALUE), 0, false, TABLE, random).ifPresent(id -> counts.merge(id, 1, Integer::sum));
        }
        int dropped = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(0.1, dropped / (double) kills, 0.01);
        assertEquals(0.75, counts.get(MAX_AMMO) / (double) dropped, 0.03);
    }

    @Test
    void zeroWeightNeverDropsAtRandom() {
        PowerUpDrops.Table onlyForced = new PowerUpDrops.Table(List.of(new PowerUpDrops.Entry(FISSION, 0)));
        assertEquals(Optional.empty(), PowerUpDrops.roll(chance(1.0, 10), 0, false, onlyForced, RandomSource.create(3)));
        assertEquals(Optional.empty(), PowerUpDrops.roll(chance(1.0, 10), 0, false, PowerUpDrops.Table.EMPTY, RandomSource.create(3)));
    }

    @Test
    void pointsReachingTheThresholdDropEvenWithNoChanceButNotPastTheCap() {
        RandomSource random = RandomSource.create(5);
        assertTrue(PowerUpDrops.roll(chance(0.0, 4), 0, true, TABLE, random).isPresent(), "the team's points are due a drop");
        assertEquals(Optional.empty(), PowerUpDrops.roll(chance(0.0, 4), 0, false, TABLE, random), "no chance, no points due");
        assertEquals(Optional.empty(), PowerUpDrops.roll(chance(0.0, 4), 4, true, TABLE, random), "the round's cap still holds");
    }
}
