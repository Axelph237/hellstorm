package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.game.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The use key held on a downed teammate. Sent every couple of ticks while held; the game checks range and state. */
public record RevivePacket(int target) {
    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(target);
    }

    static RevivePacket decode(FriendlyByteBuf buf) {
        return new RevivePacket(buf.readVarInt());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer reviver = ctx.get().getSender();
        if (reviver != null && reviver.level().getEntity(target) instanceof ServerPlayer downed) {
            GameManager.forPlayer(reviver.getUUID()).ifPresent(a -> a.game().holdRevive(reviver.getUUID(), downed.getUUID()));
        }
    }
}
