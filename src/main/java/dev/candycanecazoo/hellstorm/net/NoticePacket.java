package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.NoticeOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A short message above the hotbar, in place of the action bar, which can't wrap long lines. */
public record NoticePacket(Component message) {
    void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
    }

    static NoticePacket decode(FriendlyByteBuf buf) {
        return new NoticePacket(buf.readComponent());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NoticeOverlay.show(message));
    }
}
