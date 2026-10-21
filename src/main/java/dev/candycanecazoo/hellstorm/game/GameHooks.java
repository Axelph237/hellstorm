package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.perk.Perks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Forge events that feed running games: ticks, kills, deaths, joins and leaves, and keeping game mobs clean. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class GameHooks {
    private static final Set<MobSpawnType> NATURAL = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.EVENT);

    private GameHooks() {}

    private static boolean isGameMob(Entity entity) {
        return entity.getTags().contains(ForgeGameWorld.MOB_TAG);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GameManager.tick(event.getServer());
        }
    }

    /** Right-clicking any block of a door buys it; the click does nothing else. */
    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && Doors.use(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** Outside a game, breaking a door's block takes it out of the door, and the last block takes the door with it. */
    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || GameManager.get(level).isPresent()) {
            return;
        }
        dev.candycanecazoo.hellstorm.map.MapSavedData.get(level).map().doorAt(event.getPos()).ifPresent(door ->
                dev.candycanecazoo.hellstorm.tools.MapEdits.apply(level, m -> door.members().size() <= 1
                        ? m.withoutDoor(door.id()) : m.withDoor(door.withoutMember(event.getPos()))));
    }

    /**
     * The map is immutable during play, so no explosion in a game's level breaks a block or starts a fire (vanilla's fire
     * lands on the blocks it would break, so emptying the list stops both). It still hurts and knocks back. Guns' own
     * explosions keep the terrain everywhere (see {@code TaczEvents}).
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && GameManager.get(level).isPresent()) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GameManager.endAll();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            // A player waiting for the start dies as anyone outside a game would: the game hasn't taken them over yet.
            GameManager.playing(player.getUUID()).ifPresent(active -> {
                event.setCanceled(true);
                Perks.clearBuffs(player);
                player.setHealth(player.getMaxHealth());
                active.game().onPlayerDied(player.getUUID(), active.world());
            });
            return;
        }
        GameManager.forMob(entity.getUUID()).ifPresent(active -> {
            Optional<ServerPlayer> killer = Optional.ofNullable(event.getSource().getEntity())
                    .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast);
            boolean melee = killer.isPresent() && event.getSource().getDirectEntity() == killer.get();
            boolean headshot = killer.flatMap(k -> CombatLog.takeKillingShot(entity.getUUID(), k.getUUID(), entity.level().getGameTime()))
                    .map(CombatLog.GunHit::headshot).orElse(false);
            CombatLog.forget(entity.getUUID());
            active.game().onMobDied(entity.getUUID(), killer.map(Entity::getUUID), headshot, melee, entity.blockPosition());
        });
    }

    /** Damage that would kill a player in a game downs them instead. */
    @SubscribeEvent
    public static void onPlayerDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() >= player.getHealth()) {
            GameManager.forPlayer(player.getUUID()).filter(a -> a.level() == player.level())
                    .filter(a -> a.game().onLethalDamage(player.getUUID(), a.world()))
                    .ifPresent(a -> event.setCanceled(true));
        }
    }

    /** Every tick for players in a game, once it has started for them: no vanilla regen (food held at 17), and the downed stay down. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        GameManager.playing(player.getUUID()).filter(a -> a.level() == player.level()).ifPresent(active -> {
            player.getFoodData().setFoodLevel(Downs.FOOD_LEVEL);
            player.getFoodData().setSaturation(0);
            if (active.game().player(player.getUUID()).map(s -> s.life() instanceof LifeState.Downed).orElse(false)) {
                Downs.enforce(player);
            }
        });
    }

    /** Right-clicking a downed teammate revives them while held; vanilla repeats the click every few ticks. */
    @SubscribeEvent
    public static void onInteractPlayer(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer reviver && event.getTarget() instanceof ServerPlayer downed
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            GameManager.forPlayer(reviver.getUUID()).ifPresent(a -> a.game().holdRevive(reviver.getUUID(), downed.getUUID()));
        }
    }

    /** Melee hits that don't kill. Gun hits come from TacZ's own event, whose damage arrives through a bullet, not the player. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.level().isClientSide() && target instanceof ServerPlayer hurt) {
            GameManager.forPlayer(hurt.getUUID()).ifPresent(a -> a.game().onPlayerHurt(hurt.getUUID()));
        }
        if (!target.level().isClientSide() && event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() == player && event.getAmount() < target.getHealth()) {
            GameManager.forMob(target.getUUID()).ifPresent(a -> a.game().onMobHit(target.getUUID(), player.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isGameMob(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isGameMob(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Game zombies never burn, so sunlight and fire don't thin out a round. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (isGameMob(event.getEntity()) && event.getEntity().isOnFire()) {
            event.getEntity().clearFire();
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        boolean burningZombie = isGameMob(event.getEntity()) && event.getSource().is(DamageTypeTags.IS_FIRE);
        // /hs debug god: the player takes no damage at all, in or out of a game.
        boolean god = event.getEntity() instanceof ServerPlayer player && Cheats.has(player.getUUID(), Cheats.Cheat.GOD);
        // Downed players are out of the fight: nothing hurts them until they're revived or bleed out.
        boolean downed = event.getEntity() instanceof ServerPlayer player && GameManager.forPlayer(player.getUUID())
                .flatMap(a -> a.game().player(player.getUUID())).map(s -> s.life() instanceof LifeState.Downed).orElse(false);
        boolean notFighting = event.getEntity() instanceof ServerPlayer player && GameManager.forPlayer(player.getUUID())
                .map(a -> a.game().protectsPlayers()).orElse(false);
        if (burningZombie || god || downed || notFighting) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onNaturalSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (NATURAL.contains(event.getSpawnType()) && event.getLevel() instanceof ServerLevel level
                && GameManager.get(level.getLevel()).isPresent()) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * A tagged mob loaded from disk is left over from a crash or restart, since games aren't saved: remove it.
     * Fresh spawns also fire this event, before the game records them, so only disk loads are checked.
     */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && isGameMob(event.getEntity())
                && GameManager.forMob(event.getEntity().getUUID()).isEmpty()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && isGameMob(event.getEntity()) && event.getEntity().isAlive()) {
            GameManager.forMob(event.getEntity().getUUID()).ifPresent(a -> a.game().onMobLost(event.getEntity().getUUID()));
        }
    }

    /**
     * Nobody is saved crawling. Someone still waiting to reconnect keeps their perks; someone who left the game doesn't.
     * Someone waiting for the start leaves as they came, with their own effects.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GameManager.forPlayer(id).ifPresent(a -> {
            boolean waiting = a.game().player(id).map(PlayerState::waiting).orElse(false);
            a.game().disconnect(id);
            if (!waiting && event.getEntity() instanceof ServerPlayer player) {
                Downs.standUp(player);
                if (a.game().player(id).isEmpty()) {
                    Perks.clearBuffs(player);
                }
            }
        });
    }

    /**
     * Back in time: carry on in the game (a spectator still spectates). Otherwise, someone who left mid-game, outlasted
     * the grace time, or was in a game when the server crashed gets their normal state back here.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<GameManager.Active> game = GameManager.forPlayer(player.getUUID());
        if (game.isPresent() && game.get().game().reconnect(player.getUUID())) {
            // The server may log them in with its own game mode (a LAN world's default, say), so put the game's back.
            if (game.get().game().player(player.getUUID()).map(s -> s.life() instanceof LifeState.Spectating).orElse(false)) {
                game.get().world().spectate(player.getUUID());
            } else {
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            }
        } else if (game.isEmpty()) {
            Sessions.get(player.server).restore(player);
        }
    }
}
