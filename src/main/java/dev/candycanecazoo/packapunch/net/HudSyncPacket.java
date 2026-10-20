package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.HudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the client needs to tell a player whether they can buy what they're looking at. Sent with each status update. */
public record HudSyncPacket(int points, boolean powerOn) {
    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(points);
        buf.writeBoolean(powerOn);
    }

    static HudSyncPacket decode(FriendlyByteBuf buf) {
        return new HudSyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HudState.update(this));
    }
}
