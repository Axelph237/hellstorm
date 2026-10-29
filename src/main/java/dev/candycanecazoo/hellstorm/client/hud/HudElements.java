package dev.candycanecazoo.hellstorm.client.hud;

import java.util.List;

/** Every element of the game HUD, in the order they're drawn. Each has its own client config section. */
public final class HudElements {
    public static final RoundCounter ROUND = new RoundCounter();
    public static final PlayerBoard PLAYERS = new PlayerBoard();
    public static final PowerUpTimers TIMERS = new PowerUpTimers();
    public static final ProgressBar BAR = new ProgressBar();
    public static final List<HudElement> ALL = List.of(ROUND, PLAYERS, TIMERS, BAR);

    private HudElements() {}
}
