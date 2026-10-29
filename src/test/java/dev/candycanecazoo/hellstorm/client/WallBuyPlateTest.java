package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallBuyPlateTest {
    private static final double EPSILON = 1e-9;

    @Test
    void aLongGunGetsAPlateItsLengthPlusABorder() {
        WallBuyPlate plate = WallBuyPlate.around(-0.6, -0.2, 0.6, 0.2);
        assertEquals(1.2 + 2 * WallBuyPlate.BORDER, plate.width(), EPSILON);
        assertEquals(WallBuyPlate.MIN, plate.height(), EPSILON, "a short gun's plate is still 0.75 tall");
    }

    @Test
    void aSmallItemGetsTheSmallestPlateCentredOnIt() {
        WallBuyPlate plate = WallBuyPlate.around(0.1, 0.1, 0.3, 0.3);
        assertEquals(WallBuyPlate.MIN, plate.width(), EPSILON);
        assertEquals(WallBuyPlate.MIN, plate.height(), EPSILON);
        assertEquals(0.2, (plate.minX() + plate.maxX()) / 2, EPSILON);
        assertEquals(0.2, (plate.minY() + plate.maxY()) / 2, EPSILON);
    }

    @Test
    void anEmptyWallBuyHasTheSmallestPlateOnTheBlocksCenter() {
        assertEquals(new WallBuyPlate(-0.375, -0.375, 0.375, 0.375), WallBuyPlate.EMPTY);
    }
}
