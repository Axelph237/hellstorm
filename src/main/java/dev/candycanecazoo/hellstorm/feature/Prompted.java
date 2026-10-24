package dev.candycanecazoo.hellstorm.feature;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** A block entity that tells players looking at it what they can buy there. Read on the client from synced data. */
public interface Prompted {
    /**
     * Shown as "{label} {cost}", for example "AK-47 1000", or just the label when it's free, with any {@code details}
     * on the lines below it (a vending machine's drink lore).
     */
    record Prompt(String label, int cost, boolean needsPower, List<Component> details) {
        public Prompt {
            details = List.copyOf(details);
        }

        public Prompt(String label, int cost, boolean needsPower) {
            this(label, cost, needsPower, List.of());
        }

        public Prompt withDetails(List<Component> lines) {
            return new Prompt(label, cost, needsPower, lines);
        }
    }

    /** Empty when there's nothing to buy right now (the power is already on, the box is rolling). */
    Optional<Prompt> prompt();
}
