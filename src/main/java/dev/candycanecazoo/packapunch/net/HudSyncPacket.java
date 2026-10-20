package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.game.GameWorld;
import dev.candycanecazoo.packapunch.client.HudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The game HUD for one player: the round, everyone's points, whether purchases need power, the team's timed effects, and
 * this player's progress bar. Sent a few times a second.
 */
public record HudSyncPacket(int round, String phase, int zombiesLeft, boolean powerOn, int points, List<Line> players,
                            List<GameWorld.Timer> timers, Optional<GameWorld.Bar> bar) {
    /** One player's row in the points list. {@code you} marks the receiving player's own row. */
    public record Line(String name, int points, boolean alive, boolean downed, boolean you) {}

    public static final HudSyncPacket NONE = new HudSyncPacket(0, "", 0, false, 0, List.of(), List.of(), Optional.empty());

    public HudSyncPacket {
        players = List.copyOf(players);
        timers = List.copyOf(timers);
    }

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
            buf.writeBoolean(line.downed());
            buf.writeBoolean(line.you());
        }
        buf.writeVarInt(timers.size());
        for (GameWorld.Timer timer : timers) {
            buf.writeUtf(timer.name());
            buf.writeVarInt(timer.ticksLeft());
            buf.writeVarInt(timer.totalTicks());
        }
        buf.writeOptional(bar, (b, value) -> {
            b.writeUtf(value.label());
            b.writeFloat(value.progress());
            b.writeInt(value.color());
        });
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
            players.add(new Line(buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
        int timerCount = buf.readVarInt();
        List<GameWorld.Timer> timers = new java.util.ArrayList<>(timerCount);
        for (int i = 0; i < timerCount; i++) {
            timers.add(new GameWorld.Timer(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
        }
        Optional<GameWorld.Bar> bar = buf.readOptional(b -> new GameWorld.Bar(b.readUtf(), b.readFloat(), b.readInt()));
        return new HudSyncPacket(round, phase, zombiesLeft, powerOn, points, players, timers, bar);
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HudState.update(this));
    }
}
