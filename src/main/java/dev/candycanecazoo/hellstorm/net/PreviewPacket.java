package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.ClientMapState;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Boxes from a pending `/hs zones detect`, drawn until confirmed or cancelled. An empty list clears the preview. */
public record PreviewPacket(List<BlockBox> boxes) {
    void encode(FriendlyByteBuf buf) {
        buf.writeCollection(boxes, (b, element) -> b.writeWithCodec(NbtOps.INSTANCE, BlockBox.CODEC, element));
    }

    static PreviewPacket decode(FriendlyByteBuf buf) {
        return new PreviewPacket(buf.readList(b -> b.readWithCodec(NbtOps.INSTANCE, BlockBox.CODEC)));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setPreview(boxes));
    }
}
