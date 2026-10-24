package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import dev.candycanecazoo.hellstorm.mob.AiInjector;
import dev.candycanecazoo.hellstorm.mob.goals.BreakBarricadeGoal;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import dev.candycanecazoo.hellstorm.mob.path.MovementClass;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import dev.candycanecazoo.hellstorm.mob.MobFactory;
import dev.candycanecazoo.hellstorm.mob.MobProfile;
import dev.candycanecazoo.hellstorm.mob.Runners;
import dev.candycanecazoo.hellstorm.mob.SupportTier;
import dev.candycanecazoo.hellstorm.perk.PerkEffects;
import dev.candycanecazoo.hellstorm.perk.Perks;
import dev.candycanecazoo.hellstorm.tools.DebugRules;
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
    public static final String MOB_TAG = Hellstorm.MOD_ID + "_game";

    private final ServerLevel level;
    private final GameMap map;
    private final int tearTicks;
    private final dev.candycanecazoo.hellstorm.config.GameSettings settings;
    private final List<BlockPos> startCells;
    private final Supplier<Collection<UUID>> players;
    private final Function<UUID, ServerPlayer> lookup;
    private final Map<ResourceLocation, MobProfile> profiles;
    private final Predicate<UUID> targetable;
    private final Set<ResourceLocation> tierWarned = new HashSet<>();
    private final GamePathing pathing;

    public ForgeGameWorld(ServerLevel level, GameMap map, List<BlockPos> startCells, Supplier<Collection<UUID>> players,
                          Function<UUID, ServerPlayer> lookup, Map<ResourceLocation, MobProfile> profiles, Predicate<UUID> targetable,
                          dev.candycanecazoo.hellstorm.config.GameSettings settings, GamePathing pathing) {
        this.level = level;
        this.settings = settings;
        this.pathing = pathing;
        this.map = map;
        this.tearTicks = settings.windows().tearTicks();
        this.startCells = List.copyOf(startCells);
        this.players = players;
        this.lookup = lookup;
        this.profiles = Map.copyOf(profiles);
        this.targetable = targetable;
    }

    private Optional<ServerPlayer> player(UUID id) {
        return Optional.ofNullable(lookup.apply(id)).filter(p -> p.level() == level);
    }

    /** The player's entity in any level, for undoing what the game put on them. */
    public Optional<ServerPlayer> entity(UUID id) {
        return Optional.ofNullable(lookup.apply(id));
    }

    @Override
    public Optional<BlockPos> playerPosition(UUID id) {
        return player(id).map(Entity::blockPosition);
    }

    @Override
    public boolean canSpawn(ResourceLocation profileId, BlockPos marker) {
        Optional<WindowDef> window = map.windowFor(marker);
        MobProfile profile = profiles.get(profileId);
        if (window.isEmpty() || profile == null) {
            return true;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(profile.entity());
        if (type == null) {
            return true;
        }
        EntityDimensions hitbox = profile.hitbox(type);
        MovementClass size = MovementClass.ofHitbox(hitbox.width, hitbox.height);
        return size.width() <= window.get().width() && size.height() <= window.get().height();
    }

    @Override
    public Optional<UUID> spawnZombie(BlockPos marker, ZombieSpec spec) {
        MobProfile profile = profiles.get(spec.profile());
        if (profile == null || !level.isLoaded(marker) || DebugRules.get(level.getServer()).get(DebugRules.Rule.NO_ZOMBIES)) {
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
        boolean guided = profile.navigation() == MobProfile.Navigation.FLOW && GamePathing.walks(mob);
        AiInjector.inject(mob, profile,
                new AiInjector.Stats(spec.health() * profile.healthMultiplier(), spec.speed() * profile.speedMultiplier(), (float) profile.damage()),
                this::targetablePlayers, guided ? new GuidedNavigator(pathing, level.random.nextInt()) : dev.candycanecazoo.hellstorm.mob.goals.Navigator.vanilla());
        if (guided && GameNavigation.canReplace(mob)) {
            GameNavigation.install(mob, pathing::shut);
        }
        if (profile.breaksBarricades() && mob instanceof PathfinderMob walker) {
            map.windowFor(marker).ifPresent(window -> walker.goalSelector.addGoal(0,
                    new BreakBarricadeGoal(walker, windowAdapter(window), tearTicks, pos -> map.index().zoneAt(pos).isPresent())));
        }
        return level.addFreshEntity(mob) ? Optional.of(mob.getUUID()) : Optional.empty();
    }

    /** Picks one column of the window for this zombie, with a small random offset, and drops both points to the ground. */
    private BreakBarricadeGoal.Window windowAdapter(WindowDef window) {
        List<BlockPos> columns = window.columns();
        BlockPos column = columns.get(level.random.nextInt(columns.size()));
        double sideways = (level.random.nextDouble() - 0.5) * 0.6;
        Direction along = window.outward().getClockWise();
        Vec3 attack = ground(column.relative(window.outward())).add(along.getStepX() * sideways, 0, along.getStepZ() * sideways)
                .add(window.outward().getStepX() * level.random.nextDouble() * 0.5, 0, window.outward().getStepZ() * level.random.nextDouble() * 0.5);
        Vec3 sill = ground(column);
        Vec3 inside = ground(column.relative(window.outward().getOpposite()));
        return new BreakBarricadeGoal.Window() {
            @Override
            public Vec3 attackPoint() {
                return attack;
            }

            @Override
            public Vec3 sillPoint() {
                return sill;
            }

            @Override
            public Vec3 insidePoint() {
                return inside;
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

    /**
     * The standing point at or below {@code cell}, on top of whatever is underneath (a slab or stair counts by its real
     * height): windows with a sill have their bottom row above the ground outside.
     */
    private Vec3 ground(BlockPos cell) {
        BlockPos at = cell;
        for (int drop = 0; drop < 3 && level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty(); drop++) {
            at = at.below();
        }
        net.minecraft.world.phys.shapes.VoxelShape floor = level.getBlockState(at.below()).getCollisionShape(level, at.below());
        double top = floor.isEmpty() ? 0 : floor.max(Direction.Axis.Y) - 1;
        return Vec3.atBottomCenterOf(at).add(0, top, 0);
    }

    /** Online, alive players of this game in this level. */
    private List<ServerPlayer> targetablePlayers() {
        return players.get().stream().filter(targetable).map(this::player).flatMap(Optional::stream).toList();
    }

    private void warnOnce(ResourceLocation id, Mob mob) {
        SupportTier tier = SupportTier.of(mob);
        if (tier != SupportTier.FULL && tierWarned.add(id)) {
            Hellstorm.LOG.warn("Mob profile {} ({}) is {}: {}", id, mob.getType().builtInRegistryHolder().key().location(), tier,
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
    public void makeRunner(UUID mob) {
        if (level.getEntity(mob) instanceof Mob runner) {
            Runners.run(runner);
        }
    }

    @Override
    public void enterGame(UUID id) {
        entity(id).ifPresent(p -> {
            Sessions.get(level.getServer()).save(p);
            Loadout.give(p, settings.players());
        });
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
            p.getFoodData().setFoodLevel(Downs.FOOD_LEVEL);
            p.clearFire();
        });
    }

    @Override
    public void spectate(UUID id) {
        player(id).ifPresent(p -> {
            Downs.standUp(p);
            p.setGameMode(GameType.SPECTATOR);
        });
    }

    @Override
    public void down(UUID id) {
        player(id).ifPresent(p -> {
            Downs.knockDown(p, settings.players());
            announce(p.getGameProfile().getName() + " is downed");
        });
    }

    @Override
    public void standUp(UUID id) {
        entity(id).ifPresent(Downs::standUp);
    }

    @Override
    public void revive(UUID id) {
        player(id).ifPresent(p -> {
            Downs.standUp(p);
            p.setHealth(p.getMaxHealth() / 2);
        });
    }

    @Override
    public void giveStartingLoadout(UUID id) {
        player(id).ifPresent(p -> Loadout.give(p, settings.players()));
    }

    @Override
    public void heal(UUID id, float amount) {
        player(id).filter(p -> p.getHealth() < p.getMaxHealth()).ifPresent(p -> p.heal(amount));
    }

    @Override
    public double reviveSpeed(UUID id) {
        return player(id).map(p -> p.getAttributeValue(dev.candycanecazoo.hellstorm.registry.PapRegistries.REVIVE_SPEED)).orElse(1.0);
    }

    @Override
    public boolean canSelfRevive(UUID id) {
        return player(id).map(p -> Perks.hasAny(p, PerkEffects.SELF_REVIVE)).orElse(false);
    }

    @Override
    public double distance(UUID a, UUID b) {
        Optional<ServerPlayer> from = player(a);
        Optional<ServerPlayer> to = player(b);
        return from.isPresent() && to.isPresent() ? from.get().distanceTo(to.get()) : Double.MAX_VALUE;
    }

    @Override
    public void announce(String message) {
        Component text = Component.literal(message).withStyle(ChatFormatting.RED);
        players.get().forEach(id -> player(id).ifPresent(p -> p.sendSystemMessage(text)));
    }

    @Override
    public void showStatus(UUID id, Status status) {
        player(id).ifPresent(p -> {
            List<dev.candycanecazoo.hellstorm.net.HudSyncPacket.Line> lines = status.standings().stream().map(s -> new dev.candycanecazoo.hellstorm.net.HudSyncPacket.Line(
                    player(s.player()).map(o -> o.getGameProfile().getName()).orElse("?"),
                    s.points(), s.alive(), s.downed(), s.player().equals(id))).toList();
            dev.candycanecazoo.hellstorm.net.PapNetwork.send(p, new dev.candycanecazoo.hellstorm.net.HudSyncPacket(
                    status.round(), status.phase(), status.zombiesLeft(), status.powerOn(), status.points(), lines, status.timers(), status.bar()));
        });
    }

    @Override
    public int boardsMissing(String window) {
        return Optional.ofNullable(map.map().windows().get(window)).map(w -> w.maxBoards() - WindowOps.boards(level, w)).orElse(0);
    }

    @Override
    public void addBoard(String window) {
        Optional.ofNullable(map.map().windows().get(window)).ifPresent(w -> WindowOps.addBoard(level, w));
    }

    @Override
    public void chaChing(UUID player) {
        player(player).ifPresent(Shop::chaChing);
    }

    @Override
    public void dropPowerUp(ResourceLocation powerUp, BlockPos pos) {
        GameManager.get(level).ifPresent(game -> dev.candycanecazoo.hellstorm.powerup.PowerUps.drop(level, game.game().settings().powerUps(), powerUp,
                Vec3.atBottomCenterOf(pos)));
    }

    @Override
    public void timedEffectEnded(ResourceLocation effect) {
        GameManager.get(level).ifPresent(game -> dev.candycanecazoo.hellstorm.powerup.PowerUps.ended(game, effect));
    }

    @Override
    public void setFog(Optional<Float> density) {
        dev.candycanecazoo.hellstorm.net.FogPacket packet = new dev.candycanecazoo.hellstorm.net.FogPacket(density.orElse(0f));
        players.get().forEach(id -> entity(id).ifPresent(p -> dev.candycanecazoo.hellstorm.net.PapNetwork.send(p, packet)));
    }

    @Override
    public void announceNamed(String key, List<String> nameKeys) {
        Component text = Component.translatable(key, nameKeys.stream().map(Component::translatable).toArray()).withStyle(ChatFormatting.RED);
        players.get().forEach(id -> player(id).ifPresent(p -> p.sendSystemMessage(text)));
    }

    @Override
    public void announceKey(String key) {
        Component text = Component.translatable(key).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        players.get().forEach(id -> player(id).ifPresent(p -> p.sendSystemMessage(text)));
    }
}
