package dev.candycanecazoo.packapunch.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last gun hit on each game mob. TacZ reports headshots on its hurt event, but a kill reaches us through
 * Forge's death event, so the headshot flag is carried from one to the other here.
 */
public final class CombatLog {
    public record GunHit(UUID attacker, boolean headshot, long gameTime) {}

    private static final Map<UUID, GunHit> LAST_GUN_HIT = new ConcurrentHashMap<>();

    private CombatLog() {}

    public static void recordGunHit(UUID mob, UUID attacker, boolean headshot, long gameTime) {
        LAST_GUN_HIT.put(mob, new GunHit(attacker, headshot, gameTime));
    }

    /** The gun hit that caused a death on this tick, if the killer made it. Forgets the mob either way. */
    public static Optional<GunHit> takeKillingShot(UUID mob, UUID killer, long gameTime) {
        GunHit hit = LAST_GUN_HIT.remove(mob);
        return Optional.ofNullable(hit).filter(h -> h.attacker().equals(killer) && gameTime - h.gameTime() <= 1);
    }

    public static void forget(UUID mob) {
        LAST_GUN_HIT.remove(mob);
    }
}
