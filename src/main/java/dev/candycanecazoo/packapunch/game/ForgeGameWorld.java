package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import dev.candycanecazoo.packapunch.mob.AiInjector;
import dev.candycanecazoo.packapunch.mob.goals.BreakBarricadeGoal;
import dev.candycanecazoo.packapunch.map.WindowDef;
import net.minecraft.world.entity.PathfinderMob;
import dev.candycanecazoo.packapunch.mob.MobFactory;
import dev.candycanecazoo.packapunch.mob.MobProfile;
import dev.candycanecazoo.packapunch.mob.SupportTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** {@link GameWorld} for a real level. Zombies are whatever entity each mob profile names, with the game's AI injected. */
public final class ForgeGameWorld implements GameWorld {
    /** Every mob a game spawns carries this tag, so leftovers from a crash can be found and removed. */
    public static final String MOB_TAG = PackAPunch.MOD_ID + "_game";

    private final ServerLevel level;
    private final GameMap map;
    private final int tearTicks;
    private final List<BlockPos> startCells;
    private final Supplier<Collection<UUID>> players;
    private final Function<UUID, ServerPlayer> lookup;
    private final Map<ResourceLocation, MobProfile> profiles;
    private final Predicate<UUID> targetable;
    private final Set<ResourceLocation> tierWarned = new HashSet<>();

    public ForgeGameWorld(ServerLevel level, GameMap map, List<BlockPos> startCells, Supplier<Collection<UUID>> players,
                          Function<UUID, ServerPlayer> lookup, Map<ResourceLocation, MobProfile> profiles, Predicate<UUID> targetable,
                          int tearTicks) {
        this.level = level;
        this.map = map;
        this.tearTicks = tearTicks;
        this.startCells = List.copyOf(startCells);
        this.players = players;
        this.lookup = lookup;
        this.profiles = Map.copyOf(profiles);
        this.targetable = targetable;
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
        MobProfile profile = profiles.get(spec.profile());
        if (profile == null || !level.isLoaded(marker)) {
            return Optional.empty();
        }
        Optional<Mob> created = MobFactory.create(level, profile, spec.profile());
        if (created.isEmpty()) {
            return Optional.empty();
        }
        Mob mob = created.get();
        warnOnce(spec.profile(), mob);
        mob.moveTo(marker.getX() + 0.5, marker.getY(), marker.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        mob.addTag(MOB_TAG);
        AiInjector.inject(mob, profile,
                new AiInjector.Stats(spec.health() * profile.healthMultiplier(), spec.speed() * profile.speedMultiplier(), (float) profile.damage()),
                this::targetablePlayers);
        if (profile.breaksBarricades() && mob instanceof PathfinderMob walker) {
            map.windowFor(marker).ifPresent(window -> walker.goalSelector.addGoal(0,
                    new BreakBarricadeGoal(walker, windowAdapter(window), tearTicks, pos -> map.index().zoneAt(pos).isPresent())));
        }
        return level.addFreshEntity(mob) ? Optional.of(mob.getUUID()) : Optional.empty();
    }

    private BreakBarricadeGoal.Window windowAdapter(WindowDef window) {
        return new BreakBarricadeGoal.Window() {
            @Override
            public BlockPos attackSpot() {
                return window.attackSpot();
            }

            @Override
            public BlockPos insideSpot() {
                return window.insideSpot();
            }

            @Override
            public int boards() {
                return WindowOps.boards(level, window);
            }

            @Override
            public void tearBoard() {
                WindowOps.tearBoard(level, window);
            }
        };
    }

    /** Online, alive players of this game in this level. */
    private List<ServerPlayer> targetablePlayers() {
        return players.get().stream().filter(targetable).map(this::player).flatMap(Optional::stream).toList();
    }

    private void warnOnce(ResourceLocation id, Mob mob) {
        SupportTier tier = SupportTier.of(mob);
        if (tier != SupportTier.FULL && tierWarned.add(id)) {
            PackAPunch.LOG.warn("Mob profile {} ({}) is {}: {}", id, mob.getType().builtInRegistryHolder().key().location(), tier,
                    tier == SupportTier.PARTIAL_BRAIN ? "brain-driven, so its AI is augmented rather than replaced"
                            : "it can't walk paths, so it targets players but isn't navigated");
        }
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
