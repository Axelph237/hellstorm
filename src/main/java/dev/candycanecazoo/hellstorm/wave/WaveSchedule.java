package dev.candycanecazoo.hellstorm.wave;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Which wave event, if any, each round of one game gets. Asked once per round, in order, with the game's seeded random,
 * so a game's special rounds follow from its seed. At most one event a round: {@code round} triggers first, then
 * {@code interval}, then {@code chance}, and ties go to the lowest id.
 */
public final class WaveSchedule {
    private final List<Map.Entry<ResourceLocation, WaveEventDef>> events;
    private final RandomSource random;
    /** Each interval event's next occurrence: which repeat it is, and the round it lands on after its jitter. */
    private final Map<ResourceLocation, Next> intervals = new HashMap<>();

    private record Next(int occurrence, int round) {}

    public WaveSchedule(Map<ResourceLocation, WaveEventDef> defs, GameSettings.WaveEvents settings, RandomSource random) {
        this.random = random;
        TreeMap<ResourceLocation, WaveEventDef> enabled = new TreeMap<>();
        if (settings.enabled()) {
            defs.forEach((id, def) -> {
                if (!settings.disabled().contains(id)) {
                    enabled.put(id, def);
                }
            });
        }
        this.events = List.copyOf(enabled.entrySet());
        for (Map.Entry<ResourceLocation, WaveEventDef> e : events) {
            if (e.getValue().trigger() instanceof WaveTrigger.Interval interval) {
                intervals.put(e.getKey(), schedule(0, interval, 0));
            }
        }
    }

    /** The rules for {@code round}. Call once per round, in increasing order. */
    public Optional<RoundRules> forRound(int round) {
        Optional<Map.Entry<ResourceLocation, WaveEventDef>> picked = events.stream()
                .filter(e -> e.getValue().trigger() instanceof WaveTrigger.Rounds r && r.rounds().contains(round)).findFirst();
        for (Map.Entry<ResourceLocation, WaveEventDef> e : events) {
            if (!(e.getValue().trigger() instanceof WaveTrigger.Interval interval)) {
                continue;
            }
            Next next = intervals.get(e.getKey());
            while (next.round() < round) {
                next = schedule(next.occurrence() + 1, interval, next.round());
            }
            if (next.round() == round) {
                if (picked.isEmpty()) {
                    picked = Optional.of(e);
                }
                next = schedule(next.occurrence() + 1, interval, next.round());
            }
            intervals.put(e.getKey(), next);
        }
        if (picked.isEmpty()) {
            for (Map.Entry<ResourceLocation, WaveEventDef> e : events) {
                if (e.getValue().trigger() instanceof WaveTrigger.Chance chance && round >= chance.fromRound() && random.nextDouble() < chance.chance()) {
                    picked = Optional.of(e);
                    break;
                }
            }
        }
        return picked.map(e -> RoundRules.of(e.getKey(), e.getValue()));
    }

    /** Occurrence {@code n} lands within {@code jitter} of {@code start + n * every}, and always after the one before. */
    private Next schedule(int occurrence, WaveTrigger.Interval interval, int after) {
        int nominal = interval.start() + occurrence * interval.every();
        int offset = interval.jitter() == 0 ? 0 : random.nextInt(2 * interval.jitter() + 1) - interval.jitter();
        return new Next(occurrence, Math.max(after + 1, nominal + offset));
    }
}
