package dev.candycanecazoo.packapunch.game;

public sealed interface GamePhase {
    record Lobby() implements GamePhase {}

    record Countdown(int ticksLeft) implements GamePhase {}

    record RoundActive(int round, int toSpawn, int spawnCooldown) implements GamePhase {}

    record Intermission(int nextRound, int ticksLeft) implements GamePhase {}

    record GameOver(int roundReached, int ticksLeft) implements GamePhase {}
}
