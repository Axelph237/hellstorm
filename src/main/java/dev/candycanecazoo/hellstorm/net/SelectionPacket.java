package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.ClientMapState;
import dev.candycanecazoo.hellstorm.tools.Selection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server's copy of this player's Zone Wand selection. The server is authoritative; the client only draws it. */
public record SelectionPacket(Selection selection) {
    void encode(FriendlyByteBuf buf) {
        buf.writeOptional(selection.first(), FriendlyByteBuf::writeBlockPos);
        buf.writeOptional(selection.second(), FriendlyByteBuf::writeBlockPos);
    }

    static SelectionPacket decode(FriendlyByteBuf buf) {
        return new SelectionPacket(new Selection(buf.readOptional(FriendlyByteBuf::readBlockPos), buf.readOptional(FriendlyByteBuf::readBlockPos)));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setSelection(selection));
    }
}
