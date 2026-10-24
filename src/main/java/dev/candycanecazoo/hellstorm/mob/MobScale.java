package dev.candycanecazoo.hellstorm.mob;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.ScalePacket;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * A mob drawn and sized {@value #SCALE} times its normal size, kept in its NBT ({@code ForgeData}) so a profile's
 * {@code scale} or {@code nbt} can give it. Its hitbox and eye height are scaled on both sides; clients are told the
 * scale and draw it scaled ({@code client/MobScales}).
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class MobScale {
    public static final String SCALE = Hellstorm.MOD_ID + "_scale";

    private MobScale() {}

    /** The entity's scale: 1 unless it has a positive {@value #SCALE}. */
    public static float of(Entity entity) {
        float scale = entity.getPersistentData().contains(SCALE, Tag.TAG_FLOAT) ? entity.getPersistentData().getFloat(SCALE) : 1f;
        return scale > 0 ? scale : 1f;
    }

    /** Sets the scale of an entity that isn't in a level yet; its size follows when it's added. */
    public static void set(Entity entity, float scale) {
        entity.getPersistentData().putFloat(SCALE, scale);
        entity.refreshDimensions();
    }

    /**
     * The hitbox: a crawler's lies prone ({@link Limbs#PRONE}), then the scale multiplies it and the eye height. One
     * listener does both, so the order is fixed.
     */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.ZOMBIE && Limbs.crawls(entity)) {
            event.setNewSize(Limbs.PRONE, true);
        }
        float scale = of(entity);
        if (scale != 1f) {
            float eye = event.getNewEyeHeight();
            event.setNewSize(event.getNewSize().scale(scale), false);
            event.setNewEyeHeight(eye * scale);
        }
    }

    /** A mob loaded with a scale, from a save or a profile's NBT, takes its size once its data is read. */
    @SubscribeEvent
    public static void onScaledJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && of(event.getEntity()) != 1f) {
            event.getEntity().refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onScaledTracking(PlayerEvent.StartTracking event) {
        float scale = of(event.getTarget());
        if (scale != 1f && event.getEntity() instanceof ServerPlayer player) {
            PapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScalePacket(event.getTarget().getId(), scale));
        }
    }
}
