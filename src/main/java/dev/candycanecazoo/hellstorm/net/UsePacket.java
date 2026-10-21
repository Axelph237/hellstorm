package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The client's Hellstorm use key, held while looking at {@code pos}. Sent repeatedly while held. A barricade is repaired
 * on every packet, at the server's own pace ({@code windows.repair_ticks}); a machine is used only on the first packet
 * of a press, so holding the key never buys twice.
 */
public record UsePacket(BlockPos pos, boolean firstPress) {
    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(firstPress);
    }

    static UsePacket decode(FriendlyByteBuf buf) {
        return new UsePacket(buf.readBlockPos(), buf.readBoolean());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            apply(player, pos, firstPress);
        }
    }

    /** What the server does with one. Returns true if it repaired a window or used a machine within reach. */
    public static boolean apply(ServerPlayer player, BlockPos pos, boolean firstPress) {
        if (!player.canReach(pos, 1.0)) {
            return false;
        }
        var state = player.serverLevel().getBlockState(pos);
        if (state.is(PapRegistries.BARRICADE.get())) {
            return WindowOps.tryRepair(player.serverLevel(), pos, player);
        }
        if (firstPress && dev.candycanecazoo.hellstorm.game.Doors.use(player, pos)) {
            return true;
        }
        if (firstPress && state.getBlock() instanceof FeatureBlock) {
            FeatureBlock.use(player, pos);
            return true;
        }
        return false;
    }
}
