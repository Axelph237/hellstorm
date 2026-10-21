package dev.candycanecazoo.hellstorm.game;

/** Why a purchase didn't go through. Shown to the player; nothing is charged. */
public sealed interface Refusal {
    String message();

    record NotPlaying() implements Refusal {
        public String message() {
            return "Join the game to buy this";
        }
    }

    /** In the game, but it hasn't started: nothing is for sale in the lobby. */
    record NotStarted() implements Refusal {
        public String message() {
            return "Wait for the game to start";
        }
    }

    record NotEnoughPoints(int cost, int points) implements Refusal {
        public String message() {
            return "Not enough points";
        }
    }

    record NeedsPower() implements Refusal {
        public String message() {
            return "The power must be on";
        }
    }

    /** A feature-specific reason, such as "You already have Vigorade" or "Your inventory is full". */
    record Because(String message) implements Refusal {}

    /**
     * A reason worded by the lang file, for messages that name a machine: {@code nameKeys} fill its {@code %s} slots with
     * the machines' own names, so a resource pack renames a machine everywhere by changing one key.
     */
    record Translated(String key, java.util.List<String> nameKeys) implements Refusal {
        public String message() {
            return key;
        }
    }
}
