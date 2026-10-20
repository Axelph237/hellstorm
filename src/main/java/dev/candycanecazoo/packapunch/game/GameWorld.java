package dev.candycanecazoo.packapunch.game;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What a {@link Game} asks of the world. The Forge side implements it; tests use a fake. */
public interface GameWorld {
    /** Round-scaled base stats. The world applies the profile's own multipliers on top. */
    record ZombieSpec(ResourceLocation profile, double health, double speed) {}

    /** {@code downed} players aren't alive but can still be revived; spectators are neither. */
    record Standing(UUID player, int points, boolean alive, boolean downed) {}

    /** A team-wide effect counting down, such as Insta-Kill. {@code name} is a lang key. */
    record Timer(String name, int ticksLeft, int totalTicks) {}

    /** A progress bar in the middle of one player's screen: reviving, being revived, bleeding out. {@code progress} is 0 to 1. */
    record Bar(String label, float progress, int color) {}

    /**
     * {@code standings} lists every player in the game, in join order. {@code timers} are the team's timed effects, and
     * {@code bar} is this player's own progress bar, if any.
     */
    record Status(int round, int points, int zombiesLeft, String phase, boolean powerOn, List<Standing> standings,
                  List<Timer> timers, Optional<Bar> bar) {}

    /** Empty when the player is offline or not in this level. */
    Optional<BlockPos> playerPosition(UUID player);

    /** Whether a profile's mob could get in from this marker: a mob too big for the marker's window can't. */
    boolean canSpawn(ResourceLocation profile, BlockPos marker);

    /** The spawned mob's id, or empty if the world refused (for example, the chunk isn't loaded). */
    Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec);

    void removeMob(UUID mob);

    /** Puts the player back into play in a start zone. */
    void respawn(UUID player);

    /** Out until the next round. Undoes being down first, if they were. */
    void spectate(UUID player);

    /** Went down: crawling, pistol only, perks gone. */
    void down(UUID player);

    /** Back up from being down, at half health. */
    void revive(UUID player);

    /** After bleeding out: a fresh starting loadout for the next round. */
    void giveStartingLoadout(UUID player);

    void heal(UUID player, float amount);

    /** How fast this player revives others, from the {@code packapunch:revive_speed} attribute: 1 is normal. */
    double reviveSpeed(UUID player);

    /** Whether a solo player going down revives themselves: they have an effect in the {@code packapunch:self_revive} tag. */
    boolean canSelfRevive(UUID player);

    /** Blocks between two players, or {@link Double#MAX_VALUE} if either isn't here. */
    double distance(UUID a, UUID b);

    void announce(String message);

    void showStatus(UUID player, Status status);

    /** A timed effect ran out (see {@link Game#startTimed}). Features that changed the world for it undo that here. */
    default void timedEffectEnded(ResourceLocation effect) {}

    /** Puts a power-up pickup in the world at {@code pos}. */
    default void dropPowerUp(ResourceLocation powerUp, BlockPos pos) {}

    /** Fog for a special round, from 0 to 1, or none. */
    default void setFog(Optional<Float> density) {}

    /** Like {@link #announce}, for a lang key each player sees in their own language. */
    default void announceKey(String key) {
        announce(key);
    }
}
