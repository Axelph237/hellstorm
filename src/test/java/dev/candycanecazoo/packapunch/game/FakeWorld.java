package dev.candycanecazoo.packapunch.game;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Records what a {@link Game} asks of the world. Players stand wherever the test puts them. */
final class FakeWorld implements GameWorld {
    final Map<UUID, BlockPos> positions = new HashMap<>();
    final List<BlockPos> spawnedAt = new ArrayList<>();
    final List<UUID> spawned = new ArrayList<>();
    final List<ZombieSpec> specs = new ArrayList<>();
    final List<UUID> removed = new ArrayList<>();
    final List<UUID> respawned = new ArrayList<>();
    final List<UUID> spectated = new ArrayList<>();
    final List<String> announcements = new ArrayList<>();
    boolean refuseSpawns;
    java.util.function.BiPredicate<net.minecraft.resources.ResourceLocation, BlockPos> canSpawn = (profile, marker) -> true;

    @Override
    public boolean canSpawn(net.minecraft.resources.ResourceLocation profile, BlockPos marker) {
        return canSpawn.test(profile, marker);
    }

    @Override
    public Optional<BlockPos> playerPosition(UUID player) {
        return Optional.ofNullable(positions.get(player));
    }

    @Override
    public Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec) {
        if (refuseSpawns) {
            return Optional.empty();
        }
        UUID mob = UUID.randomUUID();
        spawned.add(mob);
        spawnedAt.add(marker);
        specs.add(spec);
        return Optional.of(mob);
    }

    @Override
    public void removeMob(UUID mob) {
        removed.add(mob);
    }

    @Override
    public void respawn(UUID player) {
        respawned.add(player);
    }

    @Override
    public void spectate(UUID player) {
        spectated.add(player);
    }

    @Override
    public void announce(String message) {
        announcements.add(message);
    }

    @Override
    public void showStatus(UUID player, Status status) {}
}
