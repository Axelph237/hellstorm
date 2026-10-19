package dev.candycanecazoo.packapunch.game;

/** Why a player earned points. Each maps to one economy setting. */
public sealed interface PointSource {
    record Hit() implements PointSource {}

    record Kill(boolean headshot, boolean melee) implements PointSource {}
}
