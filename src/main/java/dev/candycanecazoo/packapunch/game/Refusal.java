package dev.candycanecazoo.packapunch.game;

/** Why a purchase didn't go through. Shown to the player; nothing is charged. */
public sealed interface Refusal {
    String message();

    record NotPlaying() implements Refusal {
        public String message() {
            return "Join the game to buy this";
        }
    }

    record NotEnoughPoints(int cost, int points) implements Refusal {
        public String message() {
            return "Not enough points: " + cost + " needed, you have " + points;
        }
    }

    record NeedsPower() implements Refusal {
        public String message() {
            return "The power must be on";
        }
    }

    /** A feature-specific reason, such as "You already have Juggernog" or "Your inventory is full". */
    record Because(String message) implements Refusal {}
}
