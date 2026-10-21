package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;

public final class Economy {
    private Economy() {}

    public static int award(GameSettings.Economy s, PointSource source, boolean doublePoints) {
        int base;
        if (source instanceof PointSource.Kill kill) {
            base = kill.melee() ? s.meleeKill() : kill.headshot() ? s.headshotKill() : s.kill();
        } else if (source instanceof PointSource.Repair) {
            base = s.repair();
        } else {
            base = s.hit();
        }
        return doublePoints ? base * 2 : base;
    }
}
