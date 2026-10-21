package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.WaveFog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A special round's fog, from 0 (none, which also clears it) to 1. */
public record FogPacket(float density) {
    void encode(FriendlyByteBuf buf) {
        buf.writeFloat(density);
    }

    static FogPacket decode(FriendlyByteBuf buf) {
        return new FogPacket(buf.readFloat());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WaveFog.set(density));
    }
}
