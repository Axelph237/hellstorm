package dev.candycanecazoo.hellstorm.game;

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
    /** {@code icon} is the item the HUD draws beside it: the power-up's own item. */
    record Timer(String name, dev.candycanecazoo.hellstorm.data.ItemSpec.Plain icon, int ticksLeft, int totalTicks) {}

    /**
     * A progress bar in the middle of one player's screen: reviving, being revived, bleeding out, rebuilding a board.
     * {@code progress} is 0 to 1. {@code perTick} is how far it moves each tick, so the client can move it smoothly
     * between the HUD's updates, which come only every few ticks.
     */
    record Bar(String label, float progress, int color, float perTick) {}

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

    /** The mob runs faster from now on (see {@code mob/Runners}); nothing if it already does or is gone. */
    default void makeRunner(UUID mob) {}

    /**
     * The player starts taking part: their own state (inventory, effects, game mode, position) is saved to give back when
     * they leave, and they get the starting loadout. Once per player per game, before their first {@link #respawn} or
     * {@link #spectate}.
     */
    void enterGame(UUID player);

    /** Puts the player back into play in a start zone. */
    void respawn(UUID player);

    /** Out until the next round. Undoes being down first, if they were. */
    void spectate(UUID player);

    /** Went down: crawling, pistol only, perks gone. */
    void down(UUID player);

    /** Back up from being down, at half health. */
    void revive(UUID player);

    /** No longer down, because the game is over: the crawl and the rest of being down end, nothing else changes. */
    void standUp(UUID player);

    /** After bleeding out: a fresh starting loadout for the next round. */
    void giveStartingLoadout(UUID player);

    void heal(UUID player, float amount);

    /** How fast this player revives others, from the {@code hellstorm:revive_speed} attribute: 1 is normal. */
    double reviveSpeed(UUID player);

    /** Whether a solo player going down revives themselves: they have an effect in the {@code hellstorm:self_revive} tag. */
    boolean canSelfRevive(UUID player);

    /** Blocks between two players, or {@link Double#MAX_VALUE} if either isn't here. */
    double distance(UUID a, UUID b);

    void announce(String message);

    /** {@link #announce}, worded by the lang file, with machines' names filled in from their keys (see {@link Refusal.Translated}). */
    default void announceNamed(String key, java.util.List<String> nameKeys) {
        announce(key);
    }

    void showStatus(UUID player, Status status);

    /** A timed effect ran out (see {@link Game#startTimed}). Features that changed the world for it undo that here. */
    default void timedEffectEnded(ResourceLocation effect) {}

    /** How many boards the map's window {@code window} is missing; 0 if there's no such window. */
    default int boardsMissing(String window) {
        return 0;
    }

    /** Puts one board back on {@code window}, with the sound of it going on. */
    default void addBoard(String window) {}

    /** {@code player} alone hears the cha-ching: points came to them. */
    default void chaChing(UUID player) {}

    /** Puts a power-up pickup in the world at {@code pos}. */
    default void dropPowerUp(ResourceLocation powerUp, BlockPos pos) {}

    /** Fog for a special round, from 0 to 1, or none. */
    default void setFog(Optional<Float> density) {}

    /** Like {@link #announce}, for a lang key each player sees in their own language. */
    default void announceKey(String key) {
        announce(key);
    }
}
