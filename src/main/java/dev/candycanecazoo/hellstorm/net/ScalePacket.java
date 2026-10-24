package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.MobScales;
import dev.candycanecazoo.hellstorm.mob.MobScale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A mob's {@link MobScale}, for sizing and drawing it. */
public record ScalePacket(int entity, float scale) {
    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeFloat(scale);
    }

    static ScalePacket decode(FriendlyByteBuf buf) {
        return new ScalePacket(buf.readVarInt(), buf.readFloat());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MobScales.apply(this));
    }
}
