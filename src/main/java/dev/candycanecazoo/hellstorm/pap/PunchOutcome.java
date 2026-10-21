package dev.candycanecazoo.hellstorm.pap;

import net.minecraft.resources.ResourceLocation;

/** What the machine does with an item. A rejected item goes straight back, and costs nothing. */
public sealed interface PunchOutcome {
    int cost();

    /** The item becomes the {@link PunchDef}'s result. */
    record Replace(ResourceLocation id, PunchDef def, int cost) implements PunchOutcome {}

    /** A TacZ gun with no {@code punch_id} gets the default upgrade (one more tier). */
    record UpgradeTacz(int cost) implements PunchOutcome {}

    record Rejected(RejectReason reason) implements PunchOutcome {
        @Override
        public int cost() {
            return 0;
        }
    }

    /** {@code message} is the default English, for logs and audits; players see {@link #key()} through the lang file. */
    enum RejectReason {
        NOT_PUNCHABLE("This item can't go in the upgrader"),
        UNKNOWN_PUNCH_ID("This item's upgrade doesn't exist"),
        NO_UPGRADE_PATH("This item has no upgrade"),
        MAX_TIER("This weapon is fully upgraded");

        private final String message;

        RejectReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public String key() {
            return "message.hellstorm.pap." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
