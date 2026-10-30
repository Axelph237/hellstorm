package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallBuyPlateTest {
    private static final double EPSILON = 1e-9;

    @Test
    void aLongGunGetsAPlateItsLengthPlusABorderAndTheFramesHeight() {
        WallBuyPlate plate = WallBuyPlate.around(-0.6, -0.2, 0.6, 0.2);
        assertEquals(1.2 + 2 * WallBuyPlate.BORDER, plate.width(), EPSILON);
        assertEquals(WallBuyPlate.HEIGHT, plate.height(), EPSILON, "the frame's height, whatever the gun's");
    }

    @Test
    void aSmallItemGetsTheShortestPlateCentredOnIt() {
        WallBuyPlate plate = WallBuyPlate.around(0.1, 0.1, 0.3, 0.3);
        assertEquals(WallBuyPlate.MIN, plate.width(), EPSILON);
        assertEquals(0.2, (plate.minX() + plate.maxX()) / 2, EPSILON);
        assertEquals(0.2, (plate.minY() + plate.maxY()) / 2, EPSILON);
    }

    @Test
    void anEmptyWallBuyHasTheShortestPlateOnTheBlocksCenter() {
        assertEquals(new WallBuyPlate(-0.375, -0.5, 0.375, 0.5), WallBuyPlate.EMPTY);
    }
}
