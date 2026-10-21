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
        RoomDetector.Result result = RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, 10_000);
        RoomDetector.Found found = assertInstanceOf(RoomDetector.Found.class, result);
        assertEquals(5 * 3 * 4, found.cells().size());
        assertTrue(found.cells().stream().allMatch(RoomDetectorTest::insideClosedRoom));
    }

    @Test
    void stopsAtTheVolumeCapInAnOpenWorld() {
        RoomDetector.Result result = RoomDetector.detect(BlockPos.ZERO, p -> true, 500);
        assertEquals(new RoomDetector.TooLarge(500), result);
    }

    @Test
    void aRoomExactlyAtTheCapStillFits() {
        RoomDetector.Result result = RoomDetector.detect(new BlockPos(2, 0, 2), RoomDetectorTest::insideClosedRoom, 60);
        assertInstanceOf(RoomDetector.Found.class, result);
    }

    @Test
    void startingInsideAWallReportsBlocked() {
        assertEquals(new RoomDetector.StartBlocked(), RoomDetector.detect(new BlockPos(9, 9, 9), RoomDetectorTest::insideClosedRoom, 100));
    }
}
