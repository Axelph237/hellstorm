package dev.candycanecazoo.packapunch.game;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** What a {@link Game} asks of the world. The Forge side implements it; tests use a fake. */
public interface GameWorld {
    record ZombieSpec(double health, double speed) {}

    record Status(int round, int points, int zombiesLeft, String phase) {}

    /** Empty when the player is offline or not in this level. */
    Optional<BlockPos> playerPosition(UUID player);

    /** The spawned mob's id, or empty if the world refused (for example, the chunk isn't loaded). */
    Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec);

    void removeMob(UUID mob);

    /** Puts the player back into play in a start zone. */
    void respawn(UUID player);

    void spectate(UUID player);

    void announce(String message);

    void showStatus(UUID player, Status status);
}
