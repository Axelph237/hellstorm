package dev.candycanecazoo.hellstorm.game;

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
    final List<net.minecraft.resources.ResourceLocation> timedEnded = new ArrayList<>();
    final List<UUID> downed = new ArrayList<>();
    final List<UUID> revived = new ArrayList<>();
    final List<UUID> stoodUp = new ArrayList<>();
    final List<UUID> restocked = new ArrayList<>();
    final Map<UUID, Float> healed = new HashMap<>();
    final Map<UUID, Double> reviveSpeeds = new HashMap<>();
    /** Players with Saint's Brew, or another effect in {@code hellstorm:self_revive}. */
    final java.util.Set<UUID> selfRevivers = new java.util.HashSet<>();
    final List<net.minecraft.resources.ResourceLocation> drops = new ArrayList<>();
    final List<Optional<Float>> fog = new ArrayList<>();
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

    /** Going down takes every buff, Saint's Brew included, as on a real player. */
    @Override
    public void down(UUID player) {
        downed.add(player);
        selfRevivers.remove(player);
    }

    @Override
    public void revive(UUID player) {
        revived.add(player);
    }

    @Override
    public void standUp(UUID player) {
        stoodUp.add(player);
    }

    @Override
    public void giveStartingLoadout(UUID player) {
        restocked.add(player);
    }

    @Override
    public void heal(UUID player, float amount) {
        healed.merge(player, amount, Float::sum);
    }

    @Override
    public double reviveSpeed(UUID player) {
        return reviveSpeeds.getOrDefault(player, 1.0);
    }

    @Override
    public boolean canSelfRevive(UUID player) {
        return selfRevivers.contains(player);
    }

    @Override
    public double distance(UUID a, UUID b) {
        BlockPos from = positions.get(a);
        BlockPos to = positions.get(b);
        return from == null || to == null ? Double.MAX_VALUE : Math.sqrt(from.distSqr(to));
    }

    @Override
    public void timedEffectEnded(net.minecraft.resources.ResourceLocation effect) {
        timedEnded.add(effect);
    }

    @Override
    public void dropPowerUp(net.minecraft.resources.ResourceLocation powerUp, BlockPos pos) {
        drops.add(powerUp);
    }

    @Override
    public void setFog(Optional<Float> density) {
        fog.add(density);
    }
}
