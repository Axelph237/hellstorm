package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomDetectorTest {
    /** A closed 5x3x4 interior: open inside x 0..4, y 0..2, z 0..3, walls everywhere else. */
    private static boolean insideClosedRoom(BlockPos p) {
        return p.getX() >= 0 && p.getX() <= 4 && p.getY() >= 0 && p.getY() <= 2 && p.getZ() >= 0 && p.getZ() <= 3;
    }

    @Test
    void fillsExactlyTheClosedRoom() {
        RoomDetector.Result result = RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, p -> false, 10_000);
        RoomDetector.Found found = assertInstanceOf(RoomDetector.Found.class, result);
        assertEquals(5 * 3 * 4, found.cells().size());
        assertTrue(found.cells().stream().allMatch(RoomDetectorTest::insideClosedRoom));
    }

    @Test
    void stopsAtTheVolumeCapInAnOpenWorld() {
        RoomDetector.Result result = RoomDetector.detect(BlockPos.ZERO, p -> true, p -> false, 500);
        assertEquals(new RoomDetector.TooLarge(500), result);
    }

    @Test
    void aRoomExactlyAtTheCapStillFits() {
        RoomDetector.Result result = RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, p -> false, 60);
        assertInstanceOf(RoomDetector.Found.class, result);
    }

    /** The room's floor row, y = -1 under the interior: carpets or slabs walked on. */
    private static boolean carpetUnderTheRoom(BlockPos p) {
        return p.getY() == -1 && p.getX() >= 0 && p.getX() <= 4 && p.getZ() >= 0 && p.getZ() <= 3;
    }

    @Test
    void lowFloorUnderTheFillJoinsTheRoom() {
        RoomDetector.Found found = assertInstanceOf(RoomDetector.Found.class,
                RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, RoomDetectorTest::carpetUnderTheRoom, 10_000));
        assertEquals(5 * 3 * 4 + 5 * 4, found.cells().size());
        assertTrue(found.cells().contains(new BlockPos(0, -1, 0)) && found.cells().contains(new BlockPos(4, -1, 3)));
    }

    @Test
    void startingOnALowFloorStartsFromTheCellAbove() {
        RoomDetector.Found found = assertInstanceOf(RoomDetector.Found.class,
                RoomDetector.detect(new BlockPos(2, -1, 2), RoomDetectorTest::insideClosedRoom, RoomDetectorTest::carpetUnderTheRoom, 10_000));
        assertEquals(5 * 3 * 4 + 5 * 4, found.cells().size());
    }

    /** Slabs run on under the walls and out into an open world: only those right under the room join it. */
    @Test
    void lowFloorNeverSpreadsSideways() {
        RoomDetector.Found found = assertInstanceOf(RoomDetector.Found.class,
                RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, p -> p.getY() == -1, 10_000));
        assertEquals(5 * 3 * 4 + 5 * 4, found.cells().size());
        assertFalse(found.cells().contains(new BlockPos(-1, -1, 0)), "a slab under the wall isn't under the room");
    }

    @Test
    void lowFloorCountsTowardTheCap() {
        assertEquals(new RoomDetector.TooLarge(60),
                RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, RoomDetectorTest::carpetUnderTheRoom, 60));
    }

    @Test
    void startingInsideAWallReportsBlocked() {
        assertEquals(new RoomDetector.StartBlocked(), RoomDetector.detect(new BlockPos(9, 9, 9), RoomDetectorTest::insideClosedRoom, p -> false, 100));
    }
}
