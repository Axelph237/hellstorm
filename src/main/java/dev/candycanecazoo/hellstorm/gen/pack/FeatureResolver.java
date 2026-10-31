package dev.candycanecazoo.hellstorm.gen.pack;

import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.FeatureRule;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.PoolEntry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Decides what each placeholder in a laid-out map becomes. For each kind, the placeholders at depths its rule allows are
 * shuffled with the seed, and the rule's {@code count} of them (drawn from its range, at most as many as there are; all
 * of them without a count) become the machine; the rest are taken out. Each machine gets an entry from the rule's pool by weight, among those for
 * its depth, each at most once if the rule is {@code unique}. Too few placeholders for a rule's min, or no pool entry for
 * one, is a problem: the map isn't made that way, and another layout is tried.
 */
public final class FeatureResolver {
    /** A placeholder in the laid-out map: which placed room it's in, that room's depth, and the placeholder. */
    public record Found(int room, int depth, RoomDef.Placeholder placeholder) {}

    /** What a placeholder becomes: its machine, set from {@code entry} if the rule has a pool, or nothing ({@code machine} false). */
    public record Choice(Found found, boolean machine, Optional<PoolEntry> entry) {}

    public record Resolution(List<Choice> choices, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private FeatureResolver() {}

    public static Resolution resolve(List<Found> found, GenerationRules rules, SplittableRandom random) {
        Map<PlaceholderKind, List<Found>> byKind = new EnumMap<>(PlaceholderKind.class);
        for (Found f : found) {
            byKind.computeIfAbsent(f.placeholder().kind(), k -> new ArrayList<>()).add(f);
        }
        List<Choice> choices = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (PlaceholderKind kind : PlaceholderKind.values()) {
            FeatureRule rule = rules.feature(kind);
            List<Found> all = byKind.getOrDefault(kind, List.of());
            List<Found> eligible = new ArrayList<>(all.stream().filter(f -> rule.depth().contains(f.depth())).toList());
            int min = rule.count().map(GenerationRules.Range::min).orElse(eligible.size());
            if (eligible.size() < min) {
                problems.add("only " + eligible.size() + " " + kind.id() + " placeholders at the depths allowed, fewer than " + min);
                continue;
            }
            shuffle(eligible, random);
            int max = Math.min(eligible.size(), rule.count().map(GenerationRules.Range::max).orElse(eligible.size()));
            int count = min >= max ? max : min + random.nextInt(max - min + 1);
            Set<Found> chosen = new HashSet<>(eligible.subList(0, count));
            Set<PoolEntry> used = new HashSet<>();
            for (Found f : all) {
                if (!chosen.contains(f)) {
                    choices.add(new Choice(f, false, Optional.empty()));
                    continue;
                }
                if (rule.pool().isEmpty()) {
                    choices.add(new Choice(f, true, Optional.empty()));
                    continue;
                }
                List<PoolEntry> fits = rule.pool().stream()
                        .filter(e -> e.weight() > 0 && e.depth().contains(f.depth()) && !(rule.unique() && used.contains(e))).toList();
                if (fits.isEmpty()) {
                    problems.add("no " + kind.id() + " pool entry left for depth " + f.depth());
                    continue;
                }
                PoolEntry entry = pick(fits, random);
                used.add(entry);
                choices.add(new Choice(f, true, Optional.of(entry)));
            }
        }
        return new Resolution(choices, problems);
    }

    private static <T> void shuffle(List<T> list, SplittableRandom random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    private static PoolEntry pick(List<PoolEntry> entries, SplittableRandom random) {
        int total = entries.stream().mapToInt(PoolEntry::weight).sum();
        int roll = random.nextInt(total);
        for (PoolEntry e : entries) {
            roll -= e.weight();
            if (roll < 0) {
                return e;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
