package dev.candycanecazoo.hellstorm.game;

import java.util.Optional;
import java.util.UUID;

/** Whether a player is in play, down and waiting for a revive, or out until the next round. */
public sealed interface LifeState {
    record Alive() implements LifeState {}

    /**
     * Down: crawling, with {@code bleedTicksLeft} until they bleed out. {@code reviver} is the teammate reviving them, with
     * {@code reviveTicks} of the {@code reviveNeeded} it takes; a solo Saint's Brew self-revive has no reviver.
     */
    record Downed(int bleedTicksLeft, Optional<UUID> reviver, int reviveTicks, int reviveNeeded, boolean selfReviving) implements LifeState {
        public Downed bleed() {
            return new Downed(bleedTicksLeft - 1, Optional.empty(), 0, 0, false);
        }

        public Downed revivedBy(Optional<UUID> by, int needed) {
            return new Downed(bleedTicksLeft, by, reviveTicks + 1, needed, selfReviving);
        }

        public boolean revived() {
            return reviveNeeded > 0 && reviveTicks >= reviveNeeded;
        }
    }

    record Spectating() implements LifeState {}
}
