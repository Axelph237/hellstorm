package dev.candycanecazoo.hellstorm.powerup;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxes;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Notices;
import dev.candycanecazoo.hellstorm.game.PlayerState;
import dev.candycanecazoo.hellstorm.game.TimedEffects;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What happens when a power-up is collected, for the whole team, and the Insta-Kill damage rule. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PowerUps {
    private PowerUps() {}

    /** How high over the floor a power-up's item floats: in the second block up, at a standing player's eye level. */
    static final double FLOAT_HEIGHT = 1.5;
    /** How far below where the zombie died to look for the floor, for one killed mid-jump or falling. */
    private static final int FLOOR_SEARCH = 4;

    /** Puts a dropped power-up in the game's level, floating at eye level over the floor where the zombie died. */
    public static void drop(ServerLevel level, GameSettings.PowerUps settings, ResourceLocation powerUp, Vec3 at) {
        Vec3 pos = new Vec3(at.x, floor(level, at) + FLOAT_HEIGHT - PowerUpEntity.ITEM_LIFT, at.z);
        PowerUpDefs.get(powerUp).ifPresent(def -> level.addFreshEntity(
                PowerUpEntity.create(level, powerUp, def, pos, settings.lifetimeTicks(), settings.blinkTicks())));
    }

    /** The top of the first thing to stand on at or below {@code at}, or {@code at}'s own height if there's nothing close. */
    static double floor(ServerLevel level, Vec3 at) {
        BlockPos start = BlockPos.containing(at);
        for (int down = 0; down <= FLOOR_SEARCH; down++) {
            BlockPos pos = start.below(down);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && pos.getY() + shape.max(Direction.Axis.Y) <= at.y + 0.5) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
        }
        return at.y;
    }

    /** {@code player} touched the power-up. Returns true if it was collected, so the pickup should go. */
    public static boolean collect(GameManager.Active game, ServerPlayer player, ResourceLocation powerUp) {
        Optional<PowerUpDef> def = PowerUpDefs.get(powerUp);
        if (def.isEmpty() || !game.game().collectPowerUp(powerUp, player.getUUID())) {
            return false;
        }
        apply(game, def.get());
        Component name = Component.translatable(def.get().name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        players(game).forEach(p -> Notices.show(p, name));
        return true;
    }

    /** The effect alone, for the whole team. */
    public static void apply(GameManager.Active game, PowerUpDef def) {
        GameSettings.PowerUps settings = game.game().settings().powerUps();
        def.effect().timer().ifPresent(timer -> game.game().startTimed(timer, def.name(), def.durationTicks()));
        switch (def.effect()) {
            case MAX_AMMO -> players(game).forEach(PowerUps::refillEveryGun);
            case FIRE_SALE -> MysteryBoxes.setFireSale(game, true);
            case NUKE -> {
                // Killed by nobody: no points per zombie and no drops, only the bonus.
                for (UUID mob : game.game().mobs()) {
                    if (game.level().getEntity(mob) instanceof LivingEntity living) {
                        living.kill();
                    }
                }
                game.game().awardEveryone(settings.nukeBonus());
            }
            case CARPENTER -> {
                for (WindowDef window : game.game().map().map().windows().values()) {
                    WindowOps.setBoards(game.level(), window, window.maxBoards());
                }
                game.game().awardEveryone(settings.carpenterBonus());
            }
            case INSTA_KILL, DOUBLE_POINTS -> {
            }
        }
    }

    /** A timed effect ran out. Only Fire Sale changed the world. */
    public static void ended(GameManager.Active game, ResourceLocation effect) {
        if (effect.equals(TimedEffects.FIRE_SALE)) {
            MysteryBoxes.setFireSale(game, false);
        }
    }

    private static void refillEveryGun(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                TaczBridge.refillAmmo(stack);
            }
        }
        TaczBridge.refillAmmo(player.getOffhandItem());
    }

    private static List<ServerPlayer> players(GameManager.Active game) {
        return game.game().players().stream().map(PlayerState::id).map(game.world()::entity).flatMap(Optional::stream).toList();
    }

    /** Insta-Kill: while it runs, any damage a player in the game does to one of its zombies kills it, gun or melee. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        Entity source = event.getSource().getEntity();
        if (target.level().isClientSide() || !(source instanceof ServerPlayer player)) {
            return;
        }
        GameManager.forMob(target.getUUID())
                .filter(a -> a.game().timedActive(TimedEffects.INSTA_KILL) && a.game().player(player.getUUID()).isPresent())
                // Armor still applies after this event and takes at most 80%, so five times what's left always kills.
                .ifPresent(a -> event.setAmount(Math.max(event.getAmount(), (target.getHealth() + target.getAbsorptionAmount()) * 5 + 1)));
    }
}
