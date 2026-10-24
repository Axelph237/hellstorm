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

    /**
     * What a board worth {@code award} pays, given what the player has earned from repairs this round in all and at this
     * window: no more than is left under either cap.
     */
    public static int repairPay(GameSettings.Economy s, int award, int paidThisRound, int paidAtWindow) {
        return Math.max(0, Math.min(award, Math.min(s.repairCapPerRound() - paidThisRound, s.repairCapPerWindow() - paidAtWindow)));
    }
}
