package dev.candycanecazoo.hellstorm.mob;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.net.LimbsPacket;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

/**
 * A zombie's lost limbs and whether it crawls, kept in its NBT ({@code ForgeData}) so they last through a save and a
 * profile can give them. {@value #REMOVED} holds the {@link BodyPart#bit()}s of the limbs it has lost.
 * {@value #CRAWLER}, a compound with {@value #SPEED}, makes it a crawler: its hitbox lies prone, {@link #PRONE} high,
 * its speed is multiplied by {@value #SPEED}, and it's drawn crawling along the ground. Clients are told both.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class Limbs {
    public static final String REMOVED = Hellstorm.MOD_ID + "_limbs";
    public static final String CRAWLER = Hellstorm.MOD_ID + "_crawler";
    public static final String SPEED = "speed_multiplier";
    public static final EntityDimensions PRONE = EntityDimensions.scalable(0.6f, 0.6f);
    private static final UUID CRAWL_SPEED = UUID.fromString("7f0b4d6e-3c1a-4a52-9d55-2f6c1c3e8a41");

    private Limbs() {}

    public static int removed(Entity entity) {
        return entity.getPersistentData().getByte(REMOVED);
    }

    public static boolean lost(Entity entity, BodyPart part) {
        return (removed(entity) & part.bit()) != 0;
    }

    /** The crawler's speed multiplier, if {@code entity} crawls. */
    public static Optional<Float> crawlSpeed(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(CRAWLER, Tag.TAG_COMPOUND) ? Optional.of(data.getCompound(CRAWLER).getFloat(SPEED)) : Optional.empty();
    }

    public static boolean crawls(Entity entity) {
        return entity.getPersistentData().contains(CRAWLER, Tag.TAG_COMPOUND);
    }

    /** Takes {@code part} off. Returns false if it was already gone or isn't a limb. */
    public static boolean remove(Mob mob, BodyPart part) {
        if (part.bit() == 0 || lost(mob, part)) {
            return false;
        }
        mob.getPersistentData().putByte(REMOVED, (byte) (removed(mob) | part.bit()));
        sync(mob);
        return true;
    }

    /** Makes {@code mob} crawl at {@code speed} times its speed, unless it already does. */
    public static void crawl(Mob mob, float speed) {
        if (crawls(mob)) {
            return;
        }
        CompoundTag crawler = new CompoundTag();
        crawler.putFloat(SPEED, speed);
        mob.getPersistentData().put(CRAWLER, crawler);
        applyCrawl(mob);
        sync(mob);
    }

    /** What the crawler tag does on the server: the prone hitbox and the slower speed. */
    private static void applyCrawl(Mob mob) {
        mob.refreshDimensions();
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(CRAWL_SPEED) == null) {
            speed.addPermanentModifier(new AttributeModifier(CRAWL_SPEED, "Hellstorm crawler", crawlSpeed(mob).orElse(1f) - 1,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void sync(Mob mob) {
        if (!mob.level().isClientSide()) {
            PapNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), LimbsPacket.of(mob));
        }
    }

    /** A crawler's hitbox lies prone, on both sides: each works it out from the tag. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (event.getEntity().getType() == EntityType.ZOMBIE && crawls(event.getEntity())) {
            event.setNewSize(PRONE, true);
        }
    }

    /** A zombie loaded or spawned with the crawler tag, from its profile or a save, crawls from the start. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && mob.getType() == EntityType.ZOMBIE && crawls(mob)) {
            applyCrawl(mob);
        }
    }

    /** A player who starts seeing a zombie learns its limbs and whether it crawls. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget().getType() == EntityType.ZOMBIE
                && (removed(event.getTarget()) != 0 || crawls(event.getTarget()))) {
            PapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), LimbsPacket.of(event.getTarget()));
        }
    }
}
