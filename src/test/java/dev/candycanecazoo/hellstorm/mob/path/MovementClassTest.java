package dev.candycanecazoo.hellstorm.mob.path;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementClassTest {
    private static final BlockPos NORTH = new BlockPos(0, 1, 0);
    private static final BlockPos SOUTH = new BlockPos(0, 1, 3);

    private static TestWorld wallWithGap(String wall) {
        return TestWorld.floorPlan(
                "......",
                "......",
                wall,
                "......",
                "......");
    }

    private static boolean passes(TestWorld world, MovementClass movementClass) {
        return BlockAStar.DEFAULT.find(world.grid(movementClass), NORTH, SOUTH, 10_000).isPresent();
    }

    @Test
    void aOneWideGapAdmitsAOneWideMobOnly() {
        TestWorld world = wallWithGap("###.##");
        assertTrue(passes(world, new MovementClass(1, 2)));
        assertFalse(passes(world, new MovementClass(2, 2)));
    }

    @Test
    void aTwoWideGapAdmitsATwoWideMob() {
        assertTrue(passes(wallWithGap("###..#"), new MovementClass(2, 2)));
    }

    @Test
    void aTwoTallGapAdmitsATwoTallMobOnly() {
        TestWorld world = wallWithGap("###.##").fill(3, 3, 2, 3, TestWorld.TOP, 2);
        assertTrue(passes(world, new MovementClass(1, 2)));
        assertFalse(passes(world, new MovementClass(1, 3)));
    }

    @Test
    void hitboxesRoundUpToWholeBlocks() {
        assertEquals(new MovementClass(1, 2), MovementClass.ofHitbox(0.6f, 1.95f));
        assertEquals(new MovementClass(2, 3), MovementClass.ofHitbox(1.95f, 2.2f));
        assertEquals(new MovementClass(1, 1), MovementClass.ofHitbox(1.0f, 1.0f));
    }
}
