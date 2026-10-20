package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.perk.Perks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
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

/** Forge events that feed running games: ticks, kills, deaths, joins and leaves, and keeping game mobs clean. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
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
        dev.candycanecazoo.packapunch.map.MapSavedData.get(level).map().doorAt(event.getPos()).ifPresent(door ->
                dev.candycanecazoo.packapunch.tools.MapEdits.apply(level, m -> door.members().size() <= 1
                        ? m.withoutDoor(door.id()) : m.withDoor(door.withoutMember(event.getPos()))));
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
            GameManager.forPlayer(player.getUUID()).ifPresent(active -> {
                event.setCanceled(true);
                Perks.removeAll(active, player);
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

    /** Melee hits that don't kill. Gun hits come from TacZ's own event, whose damage arrives through a bullet, not the player. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
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
        // /pap debug god: the player takes no damage at all, in or out of a game.
        boolean god = event.getEntity() instanceof ServerPlayer player && Cheats.has(player.getUUID(), Cheats.Cheat.GOD);
        if (burningZombie || god) {
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

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GameManager.forPlayer(event.getEntity().getUUID()).ifPresent(a -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Perks.removeAll(a, player);
            }
            a.game().leave(event.getEntity().getUUID());
        });
    }

    /** Someone who left mid-game (or was in a game when the server crashed) gets their normal state back here. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && GameManager.forPlayer(player.getUUID()).isEmpty()) {
            Sessions.get(player.server).restore(player);
        }
    }
}
