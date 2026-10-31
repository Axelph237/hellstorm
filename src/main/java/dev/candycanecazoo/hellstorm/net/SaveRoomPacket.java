package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.feature.MachineSettings;
import dev.candycanecazoo.hellstorm.game.Notices;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.gen.place.RoomSaver;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** The Room Block screen's Save room button: its settings, saved first, then the room saved into its pack. */
public record SaveRoomPacket(BlockPos pos, CompoundTag settings) {
    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(settings);
    }

    static SaveRoomPacket decode(FriendlyByteBuf buf) {
        return new SaveRoomPacket(buf.readBlockPos(), buf.readNbt());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        Optional<String> refused = MachineSettings.save(player, pos, settings);
        PapNetwork.send(player, new SettingsResultPacket(pos, refused));
        if (refused.isPresent() || !(player.serverLevel().getBlockEntity(pos) instanceof RoomBlock.Entity block)) {
            return;
        }
        RoomSaver.Result result = RoomSaver.save(player.serverLevel(), block);
        Notices.show(player, Component.literal(result.message()).withStyle(result.saved() ? ChatFormatting.GREEN : ChatFormatting.RED));
        result.warnings().forEach(w -> Notices.show(player, Component.literal(w).withStyle(ChatFormatting.YELLOW)));
    }
}
