package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.feature.MachineSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The settings screen's Done button: the machine's new settings, encoded with its own codec. */
public record SaveSettingsPacket(BlockPos pos, CompoundTag settings) {
    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(settings);
    }

    static SaveSettingsPacket decode(FriendlyByteBuf buf) {
        return new SaveSettingsPacket(buf.readBlockPos(), buf.readNbt());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            PapNetwork.send(player, new SettingsResultPacket(pos, MachineSettings.save(player, pos, settings)));
        }
    }
}
