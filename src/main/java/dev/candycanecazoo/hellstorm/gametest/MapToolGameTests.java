package dev.candycanecazoo.hellstorm.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.ToolState;
import dev.candycanecazoo.hellstorm.tools.ZoneWandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** The Zone Wand picks the block looked at, adding a zone, window, or door says nothing, and a cut takes blocks out of a zone. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapToolGameTests {
    private static final ZoneId ZONE = new ZoneId("gametest_tools");

    private MapToolGameTests() {}

    /** A fake player that keeps the chat sent to it, which a fake player would drop. */
    private static final class Reader extends FakePlayer {
        final List<Component> said = new ArrayList<>();

        Reader(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "pap_map_tools_test"));
        }

        @Override
        public void sendSystemMessage(Component message) {
            said.add(message);
        }
    }

    @GameTest(template = "floor", batch = "map_tools")
    public static void theWandPicksTheBlockLookedAtAndEditsAreSilent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        Reader player = new Reader(level);
        var commands = level.getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(2);
        try {
            BlockPos target = helper.absolutePos(new BlockPos(4, ROOM_Y, 1));
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            player.moveTo(target.getX() + 0.5, target.getY() - 1.0, target.getZ() + 3.5, 180, 0);
            player.setYHeadRot(180);
            Optional<BlockPos> picked = ZoneWandItem.targetCell(player, 5);
            helper.assertTrue(picked.equals(Optional.of(target)), "the wand should pick the stone looked at, not " + picked);

            BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)), helper.absolutePos(new BlockPos(5, ROOM_Y + 2, 5)));
            select(player, room);
            commands.performPrefixedCommand(source, "hs zone add " + ZONE.value());
            helper.assertTrue(MapSavedData.get(level).map().zone(ZONE).map(z -> z.volume() == room.volume()).orElse(false), "zone add didn't add the room");

            BlockBox slice = new BlockBox(room.minX() + 2, room.minY(), room.minZ(), room.minX() + 2, room.maxY(), room.maxZ());
            select(player, slice);
            commands.performPrefixedCommand(source, "hs zone cut " + ZONE.value());
            var cut = MapSavedData.get(level).map().zone(ZONE).orElseThrow();
            helper.assertTrue(cut.volume() == room.volume() - slice.volume(), "the cut should take exactly the slice, leaving " + cut.volume());
            helper.assertFalse(cut.contains(slice.center()), "the cut slice is still in the zone");
            helper.assertTrue(cut.contains(room.min()) && cut.contains(room.max()), "the cut took blocks beside the slice");

            BlockPos hole = helper.absolutePos(new BlockPos(6, ROOM_Y, 3));
            BlockBox opening = BlockBox.of(hole, hole.above());
            BlockPos.betweenClosed(opening.min(), opening.max()).forEach(p -> level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState()));
            select(player, opening);
            commands.performPrefixedCommand(source, "hs window add gametest_tools_window");
            helper.assertTrue(MapSavedData.get(level).map().windows().containsKey("gametest_tools_window"), "window add didn't add the window");
            helper.assertTrue(level.getBlockState(hole).is(PapRegistries.BARRICADE.get()), "the filled hole should now hold the boards");

            BlockPos door = helper.absolutePos(new BlockPos(3, ROOM_Y, 7));
            level.setBlockAndUpdate(door, Blocks.OAK_PLANKS.defaultBlockState());
            select(player, BlockBox.single(door));
            commands.performPrefixedCommand(source, "hs door create gametest_tools_door 500");
            helper.assertTrue(MapSavedData.get(level).map().doors().containsKey("gametest_tools_door"), "door create didn't add the door");
            helper.assertTrue(player.said.isEmpty(), "adding and cutting should say nothing, but said " + player.said);

            select(player, BlockBox.single(helper.absolutePos(new BlockPos(1, ROOM_Y + 6, 1))));
            commands.performPrefixedCommand(source, "hs zone cut " + ZONE.value());
            helper.assertFalse(player.said.isEmpty(), "a cut that misses the zone should say so");

            select(player, room.grown(1));
            commands.performPrefixedCommand(source, "hs zone cut " + ZONE.value());
            helper.assertTrue(MapSavedData.get(level).map().zone(ZONE).isEmpty(), "a zone cut down to nothing should be gone");
            helper.succeed();
        } finally {
            ToolState.forget(player.getUUID());
            MapEdits.apply(level, m -> before);
        }
    }

    private static void select(Reader player, BlockBox box) {
        ToolState.setSelection(player, Selection.NONE.click(box.min()).click(box.max()));
    }
}
