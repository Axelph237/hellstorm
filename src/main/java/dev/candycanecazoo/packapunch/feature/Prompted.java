package dev.candycanecazoo.packapunch.feature;

import java.util.Optional;

/** A block entity that tells players looking at it what they can buy there. Read on the client from synced data. */
public interface Prompted {
    /** Shown as "{label} {cost}", for example "AK-47 1000", or just the label when it's free. */
    record Prompt(String label, int cost, boolean needsPower) {}

    /** Empty when there's nothing to buy right now (the power is already on, the box is rolling). */
    Optional<Prompt> prompt();
}
