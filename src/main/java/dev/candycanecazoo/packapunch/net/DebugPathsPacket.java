package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.ClientMapState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** The {@code show_paths} debug view: each game mob's remaining path, and the doorway cells. */
public record DebugPathsPacket(List<MobPath> paths, List<BlockPos> doorways) {
    public record MobPath(int entityId, List<BlockPos> nodes) {}

    void encode(FriendlyByteBuf buf) {
        buf.writeCollection(paths, (b, path) -> {
            b.writeVarInt(path.entityId());
            b.writeCollection(path.nodes(), FriendlyByteBuf::writeBlockPos);
        });
        buf.writeCollection(doorways, FriendlyByteBuf::writeBlockPos);
    }

    static DebugPathsPacket decode(FriendlyByteBuf buf) {
        List<MobPath> paths = buf.readList(b -> new MobPath(b.readVarInt(), b.readList(FriendlyByteBuf::readBlockPos)));
        return new DebugPathsPacket(paths, buf.readList(FriendlyByteBuf::readBlockPos));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setDebugPaths(this));
    }
}
