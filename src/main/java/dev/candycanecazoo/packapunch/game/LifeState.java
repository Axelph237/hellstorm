package dev.candycanecazoo.packapunch.game;

import java.util.Optional;
import java.util.UUID;

/** Whether a player is in play, down and waiting for a revive, or out until the next round. */
public sealed interface LifeState {
    record Alive() implements LifeState {}

    /**
     * Down: crawling, with {@code bleedTicksLeft} until they bleed out. {@code reviver} is the teammate reviving them, with
     * {@code reviveTicks} of progress; a solo Quick Revive self-revive has no reviver.
     */
    record Downed(int bleedTicksLeft, Optional<UUID> reviver, int reviveTicks, boolean selfReviving) implements LifeState {}

    record Spectating() implements LifeState {}
}
