package dev.candycanecazoo.hellstorm.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.WindowDef;
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

/**
 * The Zone Wand picks the block looked at and hints at the add command; adding a room, window, or door says nothing; a cut
 * takes blocks out; merges join rooms and doors; and undo and redo step through every edit, blocks and all.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapToolGameTests {
    // Each test has a batch to itself: they all edit the level's one map and its one undo history.
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
            commands.performPrefixedCommand(source, "hs zones add room " + ZONE.value());
            helper.assertTrue(MapSavedData.get(level).map().zone(ZONE).map(z -> z.volume() == room.volume()).orElse(false), "room add didn't add the room");

            BlockBox slice = new BlockBox(room.minX() + 2, room.minY(), room.minZ(), room.minX() + 2, room.maxY(), room.maxZ());
            select(player, slice);
            commands.performPrefixedCommand(source, "hs zones cut room " + ZONE.value());
            var cut = MapSavedData.get(level).map().zone(ZONE).orElseThrow();
            helper.assertTrue(cut.volume() == room.volume() - slice.volume(), "the cut should take exactly the slice, leaving " + cut.volume());
            helper.assertFalse(cut.contains(slice.center()), "the cut slice is still in the zone");
            helper.assertTrue(cut.contains(room.min()) && cut.contains(room.max()), "the cut took blocks beside the slice");

            BlockPos hole = helper.absolutePos(new BlockPos(6, ROOM_Y, 3));
            BlockBox opening = BlockBox.of(hole, hole.above());
            BlockPos.betweenClosed(opening.min(), opening.max()).forEach(p -> level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState()));
            select(player, opening);
            commands.performPrefixedCommand(source, "hs zones add window gametest_tools_window");
            helper.assertTrue(MapSavedData.get(level).map().windows().containsKey("gametest_tools_window"), "window add didn't add the window");
            helper.assertTrue(level.getBlockState(hole).is(PapRegistries.BARRICADE.get()), "the filled hole should now hold the boards");

            BlockPos door = helper.absolutePos(new BlockPos(3, ROOM_Y, 7));
            level.setBlockAndUpdate(door, Blocks.OAK_PLANKS.defaultBlockState());
            select(player, BlockBox.single(door));
            commands.performPrefixedCommand(source, "hs zones add door gametest_tools_door 500");
            helper.assertTrue(MapSavedData.get(level).map().doors().containsKey("gametest_tools_door"), "door add didn't add the door");
            helper.assertTrue(player.said.isEmpty(), "adding and cutting should say nothing, but said " + player.said);

            select(player, BlockBox.single(helper.absolutePos(new BlockPos(1, ROOM_Y + 6, 1))));
            commands.performPrefixedCommand(source, "hs zones cut room " + ZONE.value());
            helper.assertFalse(player.said.isEmpty(), "a cut that misses the room should say so");

            select(player, room.grown(1));
            commands.performPrefixedCommand(source, "hs zones cut room " + ZONE.value());
            helper.assertTrue(MapSavedData.get(level).map().zone(ZONE).isEmpty(), "a room cut down to nothing should be gone");
            helper.succeed();
        } finally {
            ToolState.forget(player.getUUID());
            MapEdits.apply(level, m -> before);
        }
    }

    @GameTest(template = "floor", batch = "map_tools_hint")
    public static void theWandHintsAtAddOnlyForAFinishedSelectionOutsideEveryRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        try {
            BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 3)));
            MapEdits.apply(level, m -> m.withZone(new dev.candycanecazoo.hellstorm.map.Zone(ZONE, List.of(room), false)));
            BlockPos outside = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
            helper.assertTrue(ZoneWandItem.hint(level, Selection.NONE.click(outside)).isEmpty(), "one corner shouldn't hint");
            Optional<String> hint = ZoneWandItem.hint(level, Selection.NONE.click(outside).click(outside.offset(1, 1, 0))).map(Component::getString);
            helper.assertTrue(hint.equals(Optional.of("/hs zones add <room|window|door>")), "a finished selection outside rooms should hint, not " + hint);
            helper.assertTrue(ZoneWandItem.hint(level, Selection.NONE.click(room.max()).click(outside)).isEmpty(), "a selection touching a room shouldn't hint");
            helper.succeed();
        } finally {
            MapEdits.apply(level, m -> before);
        }
    }

    /** A new door takes the given cost or the default; adding to a door that exists keeps its price. */
    @GameTest(template = "floor", batch = "map_tools_doors")
    public static void addingADoorCreatesItOrGrowsItKeepingItsPrice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        Reader player = new Reader(level);
        var commands = level.getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(2);
        try {
            BlockPos a = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
            BlockPos b = helper.absolutePos(new BlockPos(3, ROOM_Y, 1));
            BlockPos c = helper.absolutePos(new BlockPos(5, ROOM_Y, 1));
            for (BlockPos pos : List.of(a, b, c)) {
                level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
            }
            select(player, BlockBox.single(a));
            commands.performPrefixedCommand(source, "hs zones add door gametest_default_door");
            int defaultCost = dev.candycanecazoo.hellstorm.game.GameManager.settings().doors().defaultCost();
            helper.assertTrue(door(level, "gametest_default_door").cost() == defaultCost, "a door added with no cost should cost " + defaultCost);
            select(player, BlockBox.single(b));
            commands.performPrefixedCommand(source, "hs zones add door gametest_priced_door 750");
            helper.assertTrue(door(level, "gametest_priced_door").cost() == 750, "a new door should take the cost given");
            select(player, BlockBox.single(c));
            commands.performPrefixedCommand(source, "hs zones add door gametest_priced_door 900");
            DoorDef grown = door(level, "gametest_priced_door");
            helper.assertTrue(grown.members().equals(java.util.Set.of(b, c)), "adding to a door should add the blocks, not " + grown.members());
            helper.assertTrue(grown.cost() == 750, "adding to a door should keep its price, not make it " + grown.cost());
            helper.assertTrue(player.said.isEmpty(), "adding doors should say nothing, but said " + player.said);
            helper.succeed();
        } finally {
            ToolState.forget(player.getUUID());
            MapEdits.apply(level, m -> before);
        }
    }

    /**
     * Undo takes back each edit in turn, an add that made a new door, a window with the blocks it boarded over, a merge, and
     * a cut, and redo puts them back. Edits made in between to other things are kept.
     */
    @GameTest(template = "floor", batch = "map_tools_undo")
    public static void undoAndRedoStepThroughEveryEdit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        Reader player = new Reader(level);
        var commands = level.getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(2);
        ZoneId hall = new ZoneId("gametest_undo_hall");
        ZoneId side = new ZoneId("gametest_undo_side");
        try {
            BlockBox hallBox = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)), helper.absolutePos(new BlockPos(4, ROOM_Y + 2, 5)));
            BlockBox sideBox = BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 1)), helper.absolutePos(new BlockPos(6, ROOM_Y + 2, 5)));
            select(player, hallBox);
            commands.performPrefixedCommand(source, "hs zones add room " + hall.value() + " true");
            select(player, sideBox);
            commands.performPrefixedCommand(source, "hs zones add room " + side.value());
            helper.assertTrue(map(level).zone(hall).map(z -> z.start()).orElse(false), "a new room should take the start flag given");

            BlockPos hole = helper.absolutePos(new BlockPos(0, ROOM_Y, 3));
            level.setBlockAndUpdate(hole, Blocks.STONE.defaultBlockState());
            select(player, BlockBox.single(hole));
            commands.performPrefixedCommand(source, "hs zones add window gametest_undo_window");
            helper.assertTrue(level.getBlockState(hole).is(PapRegistries.BARRICADE.get()), "the window should board the hole");

            BlockPos plank = helper.absolutePos(new BlockPos(2, ROOM_Y, 7));
            level.setBlockAndUpdate(plank, Blocks.OAK_PLANKS.defaultBlockState());
            select(player, BlockBox.single(plank));
            commands.performPrefixedCommand(source, "hs zones add door gametest_undo_door");
            helper.assertTrue(map(level).doors().containsKey("gametest_undo_door"), "door add didn't add the door");

            BlockPos marker = helper.absolutePos(new BlockPos(7, ROOM_Y, 7));
            MapEdits.apply(level, m -> m.withFeature(marker, dev.candycanecazoo.hellstorm.map.FeatureKind.SPAWN));

            commands.performPrefixedCommand(source, "hs zones merge " + side.value() + " " + hall.value());
            helper.assertTrue(map(level).zone(side).isEmpty() && map(level).zone(hall).orElseThrow().volume() == hallBox.volume() + sideBox.volume(),
                    "the named merge should fold the side room into the hall");

            commands.performPrefixedCommand(source, "hs undo");
            helper.assertTrue(map(level).zone(side).isPresent() && map(level).zone(hall).orElseThrow().volume() == hallBox.volume(),
                    "undoing the merge should bring both rooms back as they were");
            helper.assertTrue(map(level).features().containsKey(marker), "undo shouldn't take back an edit it didn't record");

            commands.performPrefixedCommand(source, "hs undo");
            helper.assertFalse(map(level).doors().containsKey("gametest_undo_door"), "undoing the door's first add should remove the door it made");
            commands.performPrefixedCommand(source, "hs undo");
            helper.assertFalse(map(level).windows().containsKey("gametest_undo_window"), "undoing the window add should remove the window");
            helper.assertTrue(level.getBlockState(hole).is(Blocks.STONE), "undoing the window add should put back the blocks it boarded over");

            commands.performPrefixedCommand(source, "hs redo");
            helper.assertTrue(map(level).windows().containsKey("gametest_undo_window") && level.getBlockState(hole).is(PapRegistries.BARRICADE.get()),
                    "redo should bring the window and its boards back");
            commands.performPrefixedCommand(source, "hs redo");
            commands.performPrefixedCommand(source, "hs redo");
            helper.assertTrue(map(level).doors().containsKey("gametest_undo_door") && map(level).zone(side).isEmpty(), "redo should replay the door and the merge");

            commands.performPrefixedCommand(source, "hs undo");
            select(player, BlockBox.of(hallBox.max(), sideBox.min()));
            commands.performPrefixedCommand(source, "hs zones merge");
            helper.assertTrue(map(level).zone(side).isEmpty() && map(level).zone(hall).orElseThrow().volume() == hallBox.volume() + sideBox.volume(),
                    "a selection merge should fold the smaller room into the bigger one");
            commands.performPrefixedCommand(source, "hs redo");
            helper.assertTrue(player.said.stream().anyMatch(c -> c.getString().equals("Unable to redo\nERROR: Nothing to redo")), "a new edit should clear what there was to redo");

            BlockBox slice = new BlockBox(hallBox.minX(), hallBox.minY(), hallBox.minZ(), sideBox.maxX(), hallBox.maxY(), hallBox.minZ());
            select(player, slice);
            commands.performPrefixedCommand(source, "hs zones cut");
            helper.assertTrue(map(level).zone(hall).orElseThrow().volume() == hallBox.volume() + sideBox.volume() - slice.volume(),
                    "a plain cut should take the selection out of the rooms it touches");
            commands.performPrefixedCommand(source, "hs undo");
            helper.assertTrue(map(level).zone(hall).orElseThrow().volume() == hallBox.volume() + sideBox.volume(), "undoing the cut should restore it");
            helper.succeed();
        } finally {
            ToolState.forget(player.getUUID());
            MapEdits.apply(level, m -> before);
        }
    }

    /**
     * A selection of two boxes that don't make one adds both to a room, and the add clears it. A refused command leaves the
     * selection, /hs zones cancel clears it, and a window can be made with no boards.
     */
    @GameTest(template = "floor", batch = "map_tools_selection")
    public static void anAddUsesTheSelectionUpAndARefusalLeavesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        Reader player = new Reader(level);
        var commands = level.getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(2);
        ZoneId room = new ZoneId("gametest_selection_room");
        try {
            BlockBox hall = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)), helper.absolutePos(new BlockPos(6, ROOM_Y + 2, 2)));
            BlockBox wing = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 3)), helper.absolutePos(new BlockPos(2, ROOM_Y + 2, 6)));
            Selection both = Selection.NONE.click(hall.min()).click(hall.max()).click(wing.min()).click(wing.max());
            ToolState.setSelection(player, both);
            commands.performPrefixedCommand(source, "hs zones add room " + room.value());
            helper.assertTrue(map(level).zone(room).map(z -> z.volume() == hall.volume() + wing.volume()).orElse(false),
                    "the room should hold both boxes of the selection");
            helper.assertTrue(ToolState.selection(player).isEmpty(), "the add should clear the selection it used, not leave "
                    + ToolState.selection(player).boxes());

            ToolState.setSelection(player, both);
            commands.performPrefixedCommand(source, "hs zones add window gametest_selection_window");
            helper.assertFalse(map(level).windows().containsKey("gametest_selection_window"), "a two-box selection shouldn't make a window");
            helper.assertTrue(player.said.stream().anyMatch(c -> c.getString().contains("one box")), "the refusal should say why, but said " + player.said);
            helper.assertTrue(ToolState.selection(player).equals(both), "a refused command should leave the selection");
            commands.performPrefixedCommand(source, "hs zones cancel");
            helper.assertTrue(ToolState.selection(player).isEmpty(), "/hs zones cancel should clear the selection");

            BlockPos hole = helper.absolutePos(new BlockPos(7, ROOM_Y, 1));
            level.setBlockAndUpdate(hole, Blocks.STONE.defaultBlockState());
            select(player, BlockBox.single(hole));
            commands.performPrefixedCommand(source, "hs zones add window gametest_open_window 0");
            helper.assertTrue(map(level).windows().containsKey("gametest_open_window") && map(level).windows().get("gametest_open_window").maxBoards() == 0,
                    "a window can be added with no boards");
            helper.assertTrue(level.getBlockState(hole).is(PapRegistries.BARRICADE.get()) && level.getBlockState(hole).getValue(BarricadeBlock.BOARDS) == 0,
                    "a no-board window's opening holds bare barricades");
            helper.succeed();
        } finally {
            ToolState.forget(player.getUUID());
            for (WindowDef window : map(level).windows().values()) {
                if (!before.windows().containsKey(window.id())) {
                    BlockPos.betweenClosed(window.opening().min(), window.opening().max()).forEach(p -> level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
                }
            }
            MapEdits.apply(level, m -> before);
        }
    }

    private static ZombiesMap map(ServerLevel level) {
        return MapSavedData.get(level).map();
    }

    private static DoorDef door(ServerLevel level, String id) {
        DoorDef door = map(level).doors().get(id);
        if (door == null) {
            throw new IllegalStateException("no door '" + id + "'");
        }
        return door;
    }

    private static void select(Reader player, BlockBox box) {
        ToolState.setSelection(player, Selection.NONE.click(box.min()).click(box.max()));
    }
}
