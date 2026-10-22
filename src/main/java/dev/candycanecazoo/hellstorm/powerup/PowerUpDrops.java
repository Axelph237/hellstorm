package dev.candycanecazoo.hellstorm.powerup;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Whether a zombie kill drops a power-up, and which one. Pure, so the cap and the odds are testable. */
public final class PowerUpDrops {
    public record Entry(ResourceLocation powerUp, int weight) {}

    /** The power-ups a game can drop, by weight. */
    public record Table(List<Entry> entries) {
        public static final Table EMPTY = new Table(List.of());

        public Table {
            entries = List.copyOf(entries);
        }

        /** The table without {@code excluded}, such as power-ups still cooling down since their last pickup. */
        public Table without(Set<ResourceLocation> excluded) {
            return excluded.isEmpty() ? this : new Table(entries.stream().filter(e -> !excluded.contains(e.powerUp())).toList());
        }

        /** A weighted pick. Empty when nothing has weight. */
        public Optional<ResourceLocation> pick(RandomSource random) {
            int total = entries.stream().mapToInt(Entry::weight).filter(w -> w > 0).sum();
            if (total <= 0) {
                return Optional.empty();
            }
            int roll = random.nextInt(total);
            for (Entry entry : entries) {
                if (entry.weight() <= 0) {
                    continue;
                }
                roll -= entry.weight();
                if (roll < 0) {
                    return Optional.of(entry.powerUp());
                }
            }
            return Optional.empty();
        }
    }

    private PowerUpDrops() {}

    /**
     * A kill's drop: none once {@code maxPerRound} have dropped this round. Otherwise a weighted pick when the team's
     * points have reached the next threshold ({@code pointsDue}), or else with {@code dropChance}.
     */
    public static Optional<ResourceLocation> roll(GameSettings.PowerUps settings, int droppedThisRound, boolean pointsDue, Table table, RandomSource random) {
        if (droppedThisRound >= settings.maxPerRound() || (!pointsDue && random.nextDouble() >= settings.dropChance())) {
            return Optional.empty();
        }
        return table.pick(random);
    }
}
