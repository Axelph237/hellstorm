package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** {@link GameWorld} for a real level. Phase 2 spawns vanilla zombies; phase 4 swaps in configurable mob profiles. */
public final class ForgeGameWorld implements GameWorld {
    /** Every mob a game spawns carries this tag, so leftovers from a crash can be found and removed. */
    public static final String MOB_TAG = PackAPunch.MOD_ID + "_game";

    private final ServerLevel level;
    private final List<BlockPos> startCells;
    private final Supplier<Collection<UUID>> players;
    private final Function<UUID, ServerPlayer> lookup;

    public ForgeGameWorld(ServerLevel level, List<BlockPos> startCells, Supplier<Collection<UUID>> players, Function<UUID, ServerPlayer> lookup) {
        this.level = level;
        this.startCells = List.copyOf(startCells);
        this.players = players;
        this.lookup = lookup;
    }

    private Optional<ServerPlayer> player(UUID id) {
        return Optional.ofNullable(lookup.apply(id)).filter(p -> p.level() == level);
    }

    @Override
    public Optional<BlockPos> playerPosition(UUID id) {
        return player(id).map(Entity::blockPosition);
    }

    @Override
    public Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec) {
        if (!level.isLoaded(marker)) {
            return Optional.empty();
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return Optional.empty();
        }
        zombie.moveTo(marker.getX() + 0.5, marker.getY(), marker.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        zombie.setBaby(false);
        zombie.setCanPickUpLoot(false);
        zombie.setPersistenceRequired();
        zombie.addTag(MOB_TAG);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(spec.health());
        zombie.setHealth((float) spec.health());
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(spec.speed());
        zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
        return level.addFreshEntity(zombie) ? Optional.of(zombie.getUUID()) : Optional.empty();
    }

    @Override
    public void removeMob(UUID mob) {
        Entity entity = level.getEntity(mob);
        if (entity != null) {
            entity.discard();
        }
    }

    @Override
    public void respawn(UUID id) {
        player(id).ifPresent(p -> {
            if (!startCells.isEmpty()) {
                BlockPos cell = startCells.get(level.random.nextInt(startCells.size()));
                p.teleportTo(level, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
            p.setGameMode(GameType.ADVENTURE);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.clearFire();
        });
    }

    @Override
    public void spectate(UUID id) {
        player(id).ifPresent(p -> p.setGameMode(GameType.SPECTATOR));
    }

    @Override
    public void announce(String message) {
        Component text = Component.literal(message).withStyle(ChatFormatting.RED);
        players.get().forEach(id -> player(id).ifPresent(p -> p.sendSystemMessage(text)));
    }

    @Override
    public void showStatus(UUID id, Status status) {
        player(id).ifPresent(p -> p.displayClientMessage(Component.literal(status.phase()).withStyle(ChatFormatting.RED)
                .append(Component.literal("   " + status.points() + " pts").withStyle(ChatFormatting.GOLD))
                .append(status.zombiesLeft() > 0
                        ? Component.literal("   " + status.zombiesLeft() + " zombies").withStyle(ChatFormatting.GRAY)
                        : Component.empty()), true));
    }
}
