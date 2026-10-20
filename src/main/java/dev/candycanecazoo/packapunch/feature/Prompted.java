package dev.candycanecazoo.packapunch.feature;

import java.util.Optional;

/** A block entity that tells players looking at it what they can buy there. Read on the client from synced data. */
public interface Prompted {
    /** {@code what} reads after "Press [key] to", for example "buy AK-47". */
    record Prompt(String what, int cost, boolean needsPower) {}

    /** Empty when there's nothing to buy right now (the power is already on, the box is rolling). */
    Optional<Prompt> prompt();
}
