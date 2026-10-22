package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.ClientMapState;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.tools.Selection;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server's copy of this player's Zone Wand selection. The server is authoritative; the client only draws it. */
public record SelectionPacket(Selection selection) {
    void encode(FriendlyByteBuf buf) {
        buf.writeCollection(selection.boxes(), (b, box) -> b.writeWithCodec(NbtOps.INSTANCE, BlockBox.CODEC, box));
        buf.writeOptional(selection.corner(), FriendlyByteBuf::writeBlockPos);
    }

    static SelectionPacket decode(FriendlyByteBuf buf) {
        return new SelectionPacket(new Selection(buf.readList(b -> b.readWithCodec(NbtOps.INSTANCE, BlockBox.CODEC)),
                buf.readOptional(FriendlyByteBuf::readBlockPos)));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setSelection(selection));
    }
}
