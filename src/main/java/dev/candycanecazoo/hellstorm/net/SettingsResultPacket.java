package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.settings.SettingsScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** The server's answer to a save: the screen closes, or stays open showing why the settings were refused. */
public record SettingsResultPacket(BlockPos pos, Optional<String> problem) {
    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeOptional(problem, FriendlyByteBuf::writeUtf);
    }

    static SettingsResultPacket decode(FriendlyByteBuf buf) {
        return new SettingsResultPacket(buf.readBlockPos(), buf.readOptional(FriendlyByteBuf::readUtf));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettingsScreens.saved(pos, problem));
    }
}
