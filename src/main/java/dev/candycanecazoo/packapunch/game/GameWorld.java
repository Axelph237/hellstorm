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

    record Standing(UUID player, int points, boolean alive) {}

    /** {@code standings} lists every player in the game, in join order. */
    record Status(int round, int points, int zombiesLeft, String phase, boolean powerOn, List<Standing> standings) {}

    /** Empty when the player is offline or not in this level. */
    Optional<BlockPos> playerPosition(UUID player);

    /** Whether a profile's mob could get in from this marker: a mob too big for the marker's window can't. */
    boolean canSpawn(ResourceLocation profile, BlockPos marker);

    /** The spawned mob's id, or empty if the world refused (for example, the chunk isn't loaded). */
    Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec);

    void removeMob(UUID mob);

    /** Puts the player back into play in a start zone. */
    void respawn(UUID player);

    void spectate(UUID player);

    void announce(String message);

    void showStatus(UUID player, Status status);
}
