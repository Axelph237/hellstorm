package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.powerup.PowerUpDrops;

/** Datapack content a game takes when it starts, as plain data: what power-ups can drop. */
public record SurvivalContent(PowerUpDrops.Table powerUps) {
    public static final SurvivalContent NONE = new SurvivalContent(PowerUpDrops.Table.EMPTY);
}
