package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.ClientMapState;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The full map of the player's current level, sent on login, level change, and every edit. */
public record MapSyncPacket(ZombiesMap map) {
    void encode(FriendlyByteBuf buf) {
        buf.writeWithCodec(NbtOps.INSTANCE, ZombiesMap.CODEC, map);
    }

    static MapSyncPacket decode(FriendlyByteBuf buf) {
        return new MapSyncPacket(buf.readWithCodec(NbtOps.INSTANCE, ZombiesMap.CODEC));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setMap(map));
    }
}
