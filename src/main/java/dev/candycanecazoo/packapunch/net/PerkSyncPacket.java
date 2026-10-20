package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.PerkHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The perks a player holds, sent whenever they change. {@code name} is a lang key. The multipliers are what the perks do
 * to the player's guns, so the client's own TacZ timing (fire rate, reload animations) matches the server's.
 */
public record PerkSyncPacket(List<Badge> perks, float rpmMultiplier, float reloadMultiplier) {
    public static final PerkSyncPacket NONE = new PerkSyncPacket(List.of(), 1f, 1f);

    public record Badge(ResourceLocation perk, String name, int color) {}

    public PerkSyncPacket {
        perks = List.copyOf(perks);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(perks.size());
        for (Badge badge : perks) {
            buf.writeResourceLocation(badge.perk());
            buf.writeUtf(badge.name());
            buf.writeInt(badge.color());
        }
        buf.writeFloat(rpmMultiplier);
        buf.writeFloat(reloadMultiplier);
    }

    static PerkSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Badge> perks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            perks.add(new Badge(buf.readResourceLocation(), buf.readUtf(), buf.readInt()));
        }
        return new PerkSyncPacket(perks, buf.readFloat(), buf.readFloat());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PerkHud.update(this));
    }
}
