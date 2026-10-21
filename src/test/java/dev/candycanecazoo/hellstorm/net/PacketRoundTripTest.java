package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.tools.Selection;
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
    void notice() {
        NoticePacket packet = new NoticePacket(net.minecraft.network.chat.Component.literal("Not enough points: 4500 needed, you have 1200")
                .withStyle(net.minecraft.ChatFormatting.RED));
        assertEquals(packet, roundTrip(packet, NoticePacket::encode, NoticePacket::decode));
    }

    @Test
    void hud() {
        HudSyncPacket full = new HudSyncPacket(7, "Round 7", 12, true, 2350,
                List.of(new HudSyncPacket.Line("alice", 2350, true, false, true), new HudSyncPacket.Line("bob", 90, false, true, false)),
                List.of(new dev.candycanecazoo.hellstorm.game.GameWorld.Timer("powerup.hellstorm.insta_kill", 412, 600)),
                Optional.of(new dev.candycanecazoo.hellstorm.game.GameWorld.Bar("Reviving bob", 0.4f, 0xFFFFFF, 0.05f)));
        for (HudSyncPacket packet : List.of(HudSyncPacket.NONE, full)) {
            assertEquals(packet, roundTrip(packet, HudSyncPacket::encode, HudSyncPacket::decode));
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
    void debugPaths() {
        DebugPathsPacket empty = new DebugPathsPacket(List.of(), List.of());
        DebugPathsPacket full = new DebugPathsPacket(
                List.of(new DebugPathsPacket.MobPath(42, List.of(new BlockPos(1, 2, 3), new BlockPos(1, 2, 4))), new DebugPathsPacket.MobPath(7, List.of())),
                List.of(new BlockPos(5, 0, 2)));
        for (DebugPathsPacket packet : List.of(empty, full)) {
            assertEquals(packet, roundTrip(packet, DebugPathsPacket::encode, DebugPathsPacket::decode));
        }
    }

    @Test
    void settings() {
        OpenSettingsPacket bare = new OpenSettingsPacket(new BlockPos(1, 2, 3), List.of(), Optional.empty(), 0);
        OpenSettingsPacket full = new OpenSettingsPacket(new BlockPos(-4, 70, 9),
                List.of(new OpenSettingsPacket.Choice(ResourceLocation.tryBuild("hellstorm", "default"), net.minecraft.network.chat.Component.literal("Default"))),
                Optional.of(new OpenSettingsPacket.Held(new dev.candycanecazoo.hellstorm.data.ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3,
                        Optional.empty()), net.minecraft.network.chat.Component.literal("Golden Apple"))), 5000);
        for (OpenSettingsPacket packet : List.of(bare, full)) {
            assertEquals(packet, roundTrip(packet, OpenSettingsPacket::encode, OpenSettingsPacket::decode));
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("cost", 250);
        SaveSettingsPacket save = new SaveSettingsPacket(new BlockPos(0, 64, 0), tag);
        assertEquals(save, roundTrip(save, SaveSettingsPacket::encode, SaveSettingsPacket::decode));
        for (SettingsResultPacket packet : List.of(new SettingsResultPacket(BlockPos.ZERO, Optional.empty()),
                new SettingsResultPacket(BlockPos.ZERO, Optional.of("Unknown item: x:y")))) {
            assertEquals(packet, roundTrip(packet, SettingsResultPacket::encode, SettingsResultPacket::decode));
        }
    }
}
