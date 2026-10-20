package dev.candycanecazoo.packapunch.wave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.util.Codecs;

import java.util.List;
import java.util.Map;

/**
 * When a wave event happens. {@code round} names rounds outright; {@code interval} repeats every {@code every} rounds from
 * {@code start}, each time moved up to {@code jitter} rounds either way; {@code chance} rolls each round from
 * {@code from_round}. When several match one round, {@code round} beats {@code interval} beats {@code chance}.
 */
public sealed interface WaveTrigger {
    /** Lower goes first. */
    int priority();

    record Rounds(List<Integer> rounds) implements WaveTrigger {
        public Rounds {
            rounds = List.copyOf(rounds);
        }

        public int priority() {
            return 0;
        }
    }

    record Interval(int every, int start, int jitter) implements WaveTrigger {
        public int priority() {
            return 1;
        }
    }

    record Chance(double chance, int fromRound) implements WaveTrigger {
        public int priority() {
            return 2;
        }
    }

    Map<String, Codec<? extends WaveTrigger>> TYPES = Map.of(
            "round", RecordCodecBuilder.<Rounds>create(i -> i.group(
                    Codec.intRange(1, 1_000_000).listOf().fieldOf("rounds").forGetter(Rounds::rounds)).apply(i, Rounds::new)),
            "interval", RecordCodecBuilder.<Interval>create(i -> i.group(
                    Codec.intRange(1, 1_000_000).fieldOf("every").forGetter(Interval::every),
                    Codecs.strictOptional(Codec.intRange(1, 1_000_000), "start", 1).forGetter(Interval::start),
                    Codecs.strictOptional(Codec.intRange(0, 1_000), "jitter", 0).forGetter(Interval::jitter)).apply(i, Interval::new)),
            "chance", RecordCodecBuilder.<Chance>create(i -> i.group(
                    Codec.doubleRange(0, 1).fieldOf("chance").forGetter(Chance::chance),
                    Codecs.strictOptional(Codec.intRange(1, 1_000_000), "from_round", 1).forGetter(Chance::fromRound)).apply(i, Chance::new)));

    static String type(WaveTrigger trigger) {
        if (trigger instanceof Rounds) {
            return "round";
        }
        return trigger instanceof Interval ? "interval" : "chance";
    }

    Codec<WaveTrigger> CODEC = Codec.STRING.partialDispatch("type",
            trigger -> DataResult.success(type(trigger)),
            type -> TYPES.containsKey(type) ? DataResult.success(TYPES.get(type))
                    : DataResult.error(() -> "Unknown wave trigger type " + type + " (" + String.join(", ", new java.util.TreeSet<>(TYPES.keySet())) + ")"));
}
