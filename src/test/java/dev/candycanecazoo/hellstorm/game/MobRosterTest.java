package dev.candycanecazoo.hellstorm.game;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MobRosterTest {
    private static final ResourceLocation WALKER = ResourceLocation.tryBuild("test", "walker");
    private static final ResourceLocation HOUND = ResourceLocation.tryBuild("test", "hound");
    private static final ResourceLocation BOSS = ResourceLocation.tryBuild("test", "boss");

    private static final MobRoster ROSTER = new MobRoster(List.of(
            new MobRoster.Entry(WALKER, 9, 1, Optional.empty()),
            new MobRoster.Entry(HOUND, 1, 5, Optional.of(10)),
            new MobRoster.Entry(BOSS, 0, 1, Optional.empty())));

    private static Map<ResourceLocation, Integer> picks(int round, int samples) {
        RandomSource random = RandomSource.create(3);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int i = 0; i < samples; i++) {
            counts.merge(ROSTER.pick(round, random).orElseThrow(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void onlyProfilesAllowedThisRoundArePicked() {
        assertEquals(Map.of(WALKER, 1000), picks(4, 1000), "hounds start at round 5, boss has weight 0");
        assertFalse(picks(11, 1000).containsKey(HOUND), "hounds stop after round 10");
    }

    @Test
    void picksFollowWeights() {
        Map<ResourceLocation, Integer> counts = picks(7, 20_000);
        assertEquals(0.1, counts.get(HOUND) / 20_000.0, 0.01);
    }

    @Test
    void nothingAllowedMeansNoPick() {
        assertTrue(new MobRoster(List.of(new MobRoster.Entry(HOUND, 1, 5, Optional.empty()))).pick(1, RandomSource.create(1)).isEmpty());
    }

    private static MobRoster.Entry entry(String id, int weight, int min, Integer max) {
        return new MobRoster.Entry(ResourceLocation.tryBuild("test", id), weight, min, Optional.ofNullable(max));
    }

    @Test
    void firstEmptyRoundFindsRoundsNothingCanSpawnIn() {
        assertEquals(Optional.of(1), new MobRoster(List.of(entry("a", 0, 1, null))).firstEmptyRound(), "all weight 0");
        assertEquals(Optional.of(4), new MobRoster(List.of(entry("a", 1, 1, 3), entry("b", 1, 5, null))).firstEmptyRound(), "a gap at round 4");
        assertEquals(Optional.empty(), new MobRoster(List.of(entry("a", 1, 1, null))).firstEmptyRound());
        assertEquals(Optional.of(4), new MobRoster(List.of(entry("a", 1, 1, 3), entry("b", 0, 1, null), entry("c", 1, 5, null))).firstEmptyRound(),
                "a weight 0 profile doesn't fill the gap");
        assertEquals(Optional.of(2), new MobRoster(List.of(entry("a", 1, 1, 1))).firstEmptyRound(), "nothing after round 1");
    }
}
