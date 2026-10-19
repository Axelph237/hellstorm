package dev.candycanecazoo.packapunch.game;

/** Phase 6 adds Downed. Until then a player who dies spectates and comes back at the next round. */
public sealed interface LifeState {
    record Alive() implements LifeState {}

    record Spectating() implements LifeState {}
}
