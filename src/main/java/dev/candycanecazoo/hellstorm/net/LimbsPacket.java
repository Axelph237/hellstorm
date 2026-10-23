package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.ZombieLimbs;
import dev.candycanecazoo.hellstorm.mob.Limbs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A zombie's lost limbs ({@link Limbs#REMOVED}) and its crawl speed, or 0 if it doesn't crawl, for drawing it. */
public record LimbsPacket(int entity, byte removed, float crawlSpeed) {
    public static LimbsPacket of(Entity zombie) {
        return new LimbsPacket(zombie.getId(), (byte) Limbs.removed(zombie), Limbs.crawlSpeed(zombie).orElse(0f));
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeByte(removed);
        buf.writeFloat(crawlSpeed);
    }

    static LimbsPacket decode(FriendlyByteBuf buf) {
        return new LimbsPacket(buf.readVarInt(), buf.readByte(), buf.readFloat());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ZombieLimbs.apply(this));
    }
}
