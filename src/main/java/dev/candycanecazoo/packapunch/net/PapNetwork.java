package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PapNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            PackAPunch.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private PapNetwork() {}

    // FriendlyByteBuf.writeWithCodec casts the encoded value to CompoundTag in 1.20.1, so only encode
    // record codecs with it. Write lists element by element (see PacketRoundTripTest).

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(MapSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapSyncPacket::encode).decoder(MapSyncPacket::decode).consumerMainThread(MapSyncPacket::handle).add();
        CHANNEL.messageBuilder(SelectionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SelectionPacket::encode).decoder(SelectionPacket::decode).consumerMainThread(SelectionPacket::handle).add();
        CHANNEL.messageBuilder(PreviewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PreviewPacket::encode).decoder(PreviewPacket::decode).consumerMainThread(PreviewPacket::handle).add();
        CHANNEL.messageBuilder(ProblemsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProblemsPacket::encode).decoder(ProblemsPacket::decode).consumerMainThread(ProblemsPacket::handle).add();
        CHANNEL.messageBuilder(DebugPathsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DebugPathsPacket::encode).decoder(DebugPathsPacket::decode).consumerMainThread(DebugPathsPacket::handle).add();
        CHANNEL.messageBuilder(HudSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudSyncPacket::encode).decoder(HudSyncPacket::decode).consumerMainThread(HudSyncPacket::handle).add();
        CHANNEL.messageBuilder(UsePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UsePacket::encode).decoder(UsePacket::decode).consumerMainThread(UsePacket::handle).add();
    }

    public static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToLevel(ServerLevel level, Object packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }
}
