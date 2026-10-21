package dev.candycanecazoo.hellstorm.wave;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveScheduleTest {
    private static final GameSettings.WaveEvents ON = new GameSettings.WaveEvents(true, List.of());
    private static final ResourceLocation HOUNDS = ResourceLocation.tryBuild("hellstorm", "hounds");
    private static final ResourceLocation BOSS = ResourceLocation.tryBuild("hellstorm", "boss");
    private static final ResourceLocation FOG = ResourceLocation.tryBuild("hellstorm", "fog");

    private static WaveEventDef def(WaveTrigger trigger) {
        return new WaveEventDef(trigger, List.of(), Optional.empty());
    }

    /** The rounds from 1 to {@code last} that get {@code event}. */
    private static List<Integer> roundsOf(WaveSchedule schedule, ResourceLocation event, int last) {
        List<Integer> rounds = new ArrayList<>();
        for (int round = 1; round <= last; round++) {
            if (schedule.forRound(round).map(RoundRules::event).filter(event::equals).isPresent()) {
                rounds.add(round);
            }
        }
        return rounds;
    }

    @Test
    void anIntervalWithoutJitterFiresOnItsRounds() {
        WaveSchedule schedule = new WaveSchedule(Map.of(HOUNDS, def(new WaveTrigger.Interval(5, 5, 0))), ON, RandomSource.create(1));
        assertEquals(List.of(5, 10, 15, 20), roundsOf(schedule, HOUNDS, 22));
    }

    @Test
    void jitterKeepsEachOccurrenceWithinRangeAndAFixedSeedRepeats() {
        Map<ResourceLocation, WaveEventDef> defs = Map.of(HOUNDS, def(new WaveTrigger.Interval(5, 5, 1)));
        List<Integer> rounds = roundsOf(new WaveSchedule(defs, ON, RandomSource.create(99)), HOUNDS, 51);
        assertEquals(10, rounds.size(), "one occurrence per 5 rounds: " + rounds);
        for (int k = 0; k < rounds.size(); k++) {
            int nominal = 5 + 5 * k;
            assertTrue(Math.abs(rounds.get(k) - nominal) <= 1, "occurrence " + k + " on round " + rounds.get(k) + ", nominal " + nominal);
        }
        assertEquals(rounds, roundsOf(new WaveSchedule(defs, ON, RandomSource.create(99)), HOUNDS, 51), "same seed, same rounds");
        boolean moved = false;
        for (long seed = 0; seed < 20 && !moved; seed++) {
            moved = !roundsOf(new WaveSchedule(defs, ON, RandomSource.create(seed)), HOUNDS, 51).equals(List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50));
        }
        assertTrue(moved, "jitter should move some occurrence for some seed");
    }

    @Test
    void roundBeatsIntervalBeatsChanceOnTheSameRound() {
        Map<ResourceLocation, WaveEventDef> defs = Map.of(
                BOSS, def(new WaveTrigger.Rounds(List.of(10))),
                HOUNDS, def(new WaveTrigger.Interval(5, 5, 0)),
                FOG, def(new WaveTrigger.Chance(1.0, 1)));
        WaveSchedule schedule = new WaveSchedule(defs, ON, RandomSource.create(3));
        List<ResourceLocation> picked = new ArrayList<>();
        for (int round = 1; round <= 15; round++) {
            picked.add(schedule.forRound(round).map(RoundRules::event).orElseThrow());
        }
        assertEquals(HOUNDS, picked.get(4), "round 5: the interval beats the chance");
        assertEquals(BOSS, picked.get(9), "round 10: the named round beats the interval");
        assertEquals(HOUNDS, picked.get(14), "round 15: the interval is still on its rhythm after being beaten");
        assertEquals(FOG, picked.get(6), "round 7: only the chance matches");
    }

    @Test
    void disabledEventsAndTurningThemOffAreRespected() {
        Map<ResourceLocation, WaveEventDef> defs = Map.of(HOUNDS, def(new WaveTrigger.Rounds(List.of(1))), FOG, def(new WaveTrigger.Rounds(List.of(1))));
        assertEquals(Optional.of(FOG), new WaveSchedule(defs, ON, RandomSource.create(1)).forRound(1).map(RoundRules::event), "ties go to the lowest id");
        assertEquals(Optional.of(HOUNDS), new WaveSchedule(defs, new GameSettings.WaveEvents(true, List.of(FOG)), RandomSource.create(1))
                .forRound(1).map(RoundRules::event));
        assertEquals(Optional.empty(), new WaveSchedule(defs, new GameSettings.WaveEvents(false, List.of()), RandomSource.create(1)).forRound(1));
    }

    @Test
    void theShippedHoundsRoundParsesIntoItsRules() throws IOException {
        WaveEventDef hounds = WaveEventDef.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/hellstorm/hellstorm/wave_events/hounds.json"))))
                .getOrThrow(false, AssertionError::new);
        assertEquals(new WaveTrigger.Interval(5, 5, 0), hounds.trigger());
        RoundRules rules = RoundRules.of(HOUNDS, hounds);
        assertEquals(List.of(ResourceLocation.tryBuild("hellstorm", "hellhound")), rules.profiles());
        assertEquals(Optional.of(ResourceLocation.tryBuild("hellstorm", "max_ammo")), rules.lastKillDrop());
        assertEquals(Optional.of(0.6f), rules.fog());
        assertEquals(1.5, rules.spawnRate(), 1e-9);
        assertEquals(0.75, rules.count(), 1e-9);
        assertEquals(Optional.of("wave.hellstorm.hounds"), rules.announce());
        assertTrue(WaveEventDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"trigger\": {\"type\": \"sometimes\"}, \"effects\": []}"))
                .error().isPresent(), "unknown trigger type");
    }
}
