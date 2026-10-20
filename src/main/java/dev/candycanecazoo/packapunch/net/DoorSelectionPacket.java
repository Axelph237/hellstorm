package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.ClientMapState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Which door this player's Door Wand is editing, so the client can outline it. */
public record DoorSelectionPacket(Optional<String> door) {
    void encode(FriendlyByteBuf buf) {
        buf.writeOptional(door, FriendlyByteBuf::writeUtf);
    }

    static DoorSelectionPacket decode(FriendlyByteBuf buf) {
        return new DoorSelectionPacket(buf.readOptional(FriendlyByteBuf::readUtf));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setSelectedDoor(door));
    }
}
