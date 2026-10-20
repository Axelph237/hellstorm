package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.HudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** The game HUD for one player: the round, everyone's points, and whether purchases need power. Sent a few times a second. */
public record HudSyncPacket(int round, String phase, int zombiesLeft, boolean powerOn, int points, List<Line> players) {
    /** One player's row in the points list. {@code you} marks the receiving player's own row. */
    public record Line(String name, int points, boolean alive, boolean you) {}

    public static final HudSyncPacket NONE = new HudSyncPacket(0, "", 0, false, 0, List.of());

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(round);
        buf.writeUtf(phase);
        buf.writeVarInt(zombiesLeft);
        buf.writeBoolean(powerOn);
        buf.writeVarInt(points);
        buf.writeVarInt(players.size());
        for (Line line : players) {
            buf.writeUtf(line.name());
            buf.writeVarInt(line.points());
            buf.writeBoolean(line.alive());
            buf.writeBoolean(line.you());
        }
    }

    static HudSyncPacket decode(FriendlyByteBuf buf) {
        int round = buf.readVarInt();
        String phase = buf.readUtf();
        int zombiesLeft = buf.readVarInt();
        boolean powerOn = buf.readBoolean();
        int points = buf.readVarInt();
        int count = buf.readVarInt();
        List<Line> players = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new Line(buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        return new HudSyncPacket(round, phase, zombiesLeft, powerOn, points, List.copyOf(players));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HudState.update(this));
    }
}
