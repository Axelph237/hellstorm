package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.game.WindowOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The client's repair key is held while looking at a barricade. Sent repeatedly while held; the server checks reach and
 * repairs at its own pace ({@code windows.repair_ticks}), exactly as holding use on the window does.
 */
public record RepairPacket(BlockPos pos) {
    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    static RepairPacket decode(FriendlyByteBuf buf) {
        return new RepairPacket(buf.readBlockPos());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            apply(player, pos);
        }
    }

    /** What the server does with one: repair if the window is within the player's reach. */
    public static boolean apply(ServerPlayer player, BlockPos pos) {
        return player.canReach(pos, 1.0) && WindowOps.tryRepair(player.serverLevel(), pos, player);
    }
}
