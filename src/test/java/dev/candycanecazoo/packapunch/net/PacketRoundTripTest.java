package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.tools.Selection;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every packet must survive a real encode and decode. Empty and non-empty lists both matter. */
class PacketRoundTripTest {
    private static <T> T roundTrip(T packet, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encode.accept(packet, buf);
        T decoded = decode.apply(buf);
        assertEquals(0, buf.readableBytes(), "decoder left bytes unread");
        return decoded;
    }

    @Test
    void mapSync() {
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("start"), List.of(new BlockBox(0, 0, 0, 3, 3, 3), new BlockBox(4, 0, 0, 6, 2, 2)), true))
                .withFeature(new BlockPos(1, 0, 1), FeatureKind.SPAWN);
        for (ZombiesMap value : List.of(ZombiesMap.EMPTY, map)) {
            MapSyncPacket packet = new MapSyncPacket(value);
            assertEquals(packet, roundTrip(packet, MapSyncPacket::encode, MapSyncPacket::decode));
        }
    }

    @Test
    void repair() {
        UsePacket packet = new UsePacket(new BlockPos(-3, -60, 12), true);
        assertEquals(packet, roundTrip(packet, UsePacket::encode, UsePacket::decode));
    }

    @Test
    void selection() {
        for (Selection value : List.of(Selection.NONE, Selection.NONE.click(new BlockPos(1, 2, 3)),
                Selection.NONE.click(new BlockPos(1, 2, 3)).click(new BlockPos(-4, 5, 6)))) {
            SelectionPacket packet = new SelectionPacket(value);
            assertEquals(packet, roundTrip(packet, SelectionPacket::encode, SelectionPacket::decode));
        }
    }

    @Test
    void preview() {
        for (List<BlockBox> value : List.of(List.<BlockBox>of(), List.of(new BlockBox(0, 0, 0, 1, 1, 1), new BlockBox(-5, 60, 2, 9, 64, 8)))) {
            PreviewPacket packet = new PreviewPacket(value);
            assertEquals(packet, roundTrip(packet, PreviewPacket::encode, PreviewPacket::decode));
        }
    }

    @Test
    void problems() {
        List<Problem> problems = List.of(
                Problem.error("zone_overlap", "Zones 'a' and 'b' overlap", new BlockPos(1, 2, 3)),
                new Problem(Problem.Severity.WARNING, "no_start_zone", "No start zone", Optional.empty()));
        for (List<Problem> value : List.of(List.<Problem>of(), problems)) {
            ProblemsPacket packet = new ProblemsPacket(value);
            assertEquals(packet, roundTrip(packet, ProblemsPacket::encode, ProblemsPacket::decode));
        }
    }

    @Test
    void perkSync() {
        PerkSyncPacket full = new PerkSyncPacket(List.of(
                new PerkSyncPacket.Badge(ResourceLocation.tryBuild("packapunch", "juggernog"), "perk.packapunch.juggernog", 0xd33a2c),
                new PerkSyncPacket.Badge(ResourceLocation.tryBuild("other", "tap"), "Tap", 0)), 1.33f);
        for (PerkSyncPacket packet : List.of(new PerkSyncPacket(List.of(), 1f), full)) {
            assertEquals(packet, roundTrip(packet, PerkSyncPacket::encode, PerkSyncPacket::decode));
        }
    }

    @Test
    void debugPaths() {
        DebugPathsPacket empty = new DebugPathsPacket(List.of(), List.of());
        DebugPathsPacket full = new DebugPathsPacket(
                List.of(new DebugPathsPacket.MobPath(42, List.of(new BlockPos(1, 2, 3), new BlockPos(1, 2, 4))), new DebugPathsPacket.MobPath(7, List.of())),
                List.of(new BlockPos(5, 0, 2)));
        for (DebugPathsPacket packet : List.of(empty, full)) {
            assertEquals(packet, roundTrip(packet, DebugPathsPacket::encode, DebugPathsPacket::decode));
        }
    }
}
