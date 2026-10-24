package dev.candycanecazoo.hellstorm.game;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/** The mob profiles a game can spawn, as plain data, so the pick is pure and testable. */
public record MobRoster(List<Entry> entries) {
    public record Entry(ResourceLocation profile, int weight, int minRound, Optional<Integer> maxRound) {
        boolean allowedIn(int round) {
            return weight > 0 && round >= minRound && maxRound.map(max -> round <= max).orElse(true);
        }
    }

    public MobRoster {
        entries = List.copyOf(entries);
    }

    /**
     * The first round, from 1, in which no profile with weight above 0 is allowed, or empty if every round has one. Weight
     * 0 is a profile only a wave event's override spawns.
     */
    public Optional<Integer> firstEmptyRound() {
        int round = 1;
        for (Entry e : entries.stream().filter(e -> e.weight() > 0).sorted(java.util.Comparator.comparingInt(Entry::minRound)).toList()) {
            if (e.minRound() > round) {
                return Optional.of(round);
            }
            if (e.maxRound().isEmpty()) {
                return Optional.empty();
            }
            round = Math.max(round, e.maxRound().get() + 1);
        }
        return Optional.of(round);
    }

    /** A weighted pick among profiles allowed this round. Empty if none is. */
    public Optional<ResourceLocation> pick(int round, RandomSource random) {
        return pick(round, random, profile -> true);
    }

    /** As {@link #pick(int, RandomSource)}, among only the profiles {@code fits} accepts (for example, ones that fit a window). */
    public Optional<ResourceLocation> pick(int round, RandomSource random, java.util.function.Predicate<ResourceLocation> fits) {
        List<Entry> allowed = entries.stream().filter(e -> e.allowedIn(round) && fits.test(e.profile())).toList();
        int total = allowed.stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Entry entry : allowed) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry.profile());
            }
        }
        return Optional.of(allowed.get(allowed.size() - 1).profile());
    }
}
