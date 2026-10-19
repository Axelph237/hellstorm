package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.BoxMerger;
import dev.candycanecazoo.packapunch.map.CellProbe;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.RoomDetector;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import dev.candycanecazoo.packapunch.tools.RoomDetection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapGameTests {
    private MapGameTests() {}

    private static BlockPos at(int x, int dy, int z) {
        return new BlockPos(x, ROOM_Y + dy, z);
    }

    @GameTest(template = "floor")
    public static void spawnMarkerRegistersAndUnregistersItself(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(at(4, 0, 4));
        helper.setBlock(at(4, 0, 4), PapRegistries.SPAWN_MARKER.get());
        helper.assertTrue(MapSavedData.get(helper.getLevel()).map().features().get(pos) == FeatureKind.SPAWN,
                "placed marker missing from the feature index");
        helper.setBlock(at(4, 0, 4), Blocks.AIR);
        helper.assertTrue(!MapSavedData.get(helper.getLevel()).map().features().containsKey(pos),
                "removed marker still in the feature index");
        helper.succeed();
    }

    /** Walls around x 1..7, z 1..6 and a ceiling at dy 3 enclose a 5x3x4 interior (x 2..6, dy 0..2, z 2..5). A torch inside doesn't block. */
    @GameTest(template = "floor")
    public static void detectsAClosedRoomBuiltFromBlocks(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 6; z++) {
                boolean wall = x == 1 || x == 7 || z == 1 || z == 6;
                for (int dy = 0; dy <= 3; dy++) {
                    if (wall || dy == 3) {
                        helper.setBlock(at(x, dy, z), Blocks.STONE);
                    }
                }
            }
        }
        helper.setBlock(at(3, 0, 3), Blocks.TORCH);
        RoomDetector.Result result = RoomDetector.detect(helper.absolutePos(at(4, 0, 3)),
                RoomDetection.openCells(helper.getLevel(), new ZoneIndex(List.of())), 1000);
        helper.assertTrue(result instanceof RoomDetector.Found, "expected a closed room, got " + result);
        RoomDetector.Found found = (RoomDetector.Found) result;
        helper.assertTrue(found.cells().size() == 5 * 3 * 4, "detected " + found.cells().size() + " cells, expected 60");
        List<BlockBox> boxes = BoxMerger.merge(found.cells());
        BlockBox expected = BlockBox.of(helper.absolutePos(at(2, 0, 2)), helper.absolutePos(at(6, 2, 5)));
        helper.assertTrue(boxes.equals(List.of(expected)), "expected one box " + expected + ", got " + boxes);
        helper.succeed();
    }

    /** A 5x2x5 room (x 2..6, dy 0..1, z 2..6) whose east part (x 5..6) already belongs to another zone. */
    @GameTest(template = "floor")
    public static void detectionStopsAtNeighbouringZones(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                boolean wall = x == 1 || x == 7 || z == 1 || z == 7;
                for (int dy = 0; dy <= 2; dy++) {
                    if (wall || dy == 2) {
                        helper.setBlock(at(x, dy, z), Blocks.STONE);
                    }
                }
            }
        }
        BlockBox neighbour = BlockBox.of(helper.absolutePos(at(5, 0, 2)), helper.absolutePos(at(6, 1, 6)));
        ZoneIndex index = new ZoneIndex(List.of(new Zone(new ZoneId("neighbour"), List.of(neighbour), false)));
        RoomDetector.Result result = RoomDetector.detect(helper.absolutePos(at(2, 0, 2)),
                RoomDetection.openCells(helper.getLevel(), index), 1000);
        helper.assertTrue(result instanceof RoomDetector.Found, "expected a closed room, got " + result);
        RoomDetector.Found found = (RoomDetector.Found) result;
        helper.assertTrue(found.cells().stream().noneMatch(neighbour::contains), "detection leaked into the neighbouring zone");
        helper.assertTrue(found.cells().size() == 3 * 2 * 5, "detected " + found.cells().size() + " cells, expected 30");
        helper.succeed();
    }

    /** Game zombies open wooden doors, so those always pass. Iron doors and fence gates pass only while open. */
    @GameTest(template = "floor")
    public static void probeClassifiesDoorsTheWayGameZombiesSeeThem(GameTestHelper helper) {
        helper.setBlock(at(2, 0, 2), Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(at(2, 1, 2), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(at(2, 0, 5), Blocks.IRON_DOOR.defaultBlockState());
        helper.setBlock(at(2, 1, 5), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(at(6, 0, 5), Blocks.OAK_FENCE_GATE);
        helper.setBlock(at(4, 0, 2), Blocks.STONE);
        helper.setBlock(at(5, 0, 2), Blocks.OAK_FENCE);
        helper.setBlock(at(6, 0, 2), Blocks.WHITE_CARPET);
        assertCell(helper, at(2, 0, 2), CellProbe.Cell.OPEN);
        assertCell(helper, at(2, 0, 5), CellProbe.Cell.CLOSED_DOOR);
        assertCell(helper, at(6, 0, 5), CellProbe.Cell.CLOSED_DOOR);
        assertCell(helper, at(4, 0, 2), CellProbe.Cell.SOLID);
        assertCell(helper, at(5, 0, 2), CellProbe.Cell.SOLID);
        assertCell(helper, at(6, 0, 2), CellProbe.Cell.OPEN);
        assertCell(helper, at(3, 0, 2), CellProbe.Cell.OPEN);
        helper.setBlock(at(7, 0, 7), PapRegistries.BARRICADE.get().defaultBlockState().setValue(dev.candycanecazoo.packapunch.feature.BarricadeBlock.BOARDS, 0));
        assertCell(helper, at(7, 0, 7), CellProbe.Cell.SOLID);
        helper.assertTrue(!RoomDetection.openCells(helper.getLevel(), new ZoneIndex(List.of())).test(helper.absolutePos(at(7, 0, 7))),
                "a bare window must still close a room for detection");
        helper.setBlock(at(2, 0, 5), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true));
        assertCell(helper, at(2, 0, 5), CellProbe.Cell.OPEN);
        helper.succeed();
    }

    private static void assertCell(GameTestHelper helper, BlockPos relative, CellProbe.Cell expected) {
        CellProbe.Cell actual = LevelCellProbe.classify(helper.getLevel(), helper.absolutePos(relative));
        helper.assertTrue(actual == expected, helper.getBlockState(relative) + " at " + relative + " was " + actual + ", expected " + expected);
    }
}
