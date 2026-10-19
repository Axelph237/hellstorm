package dev.candycanecazoo.packapunch.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockBoxTest {
    @Test
    void cornersInAnyOrderNormalize() {
        BlockBox box = BlockBox.of(new BlockPos(5, 10, -2), new BlockPos(1, 3, 4));
        assertEquals(new BlockBox(1, 3, -2, 5, 10, 4), box);
        assertEquals(5 * 8 * 7, box.volume());
    }

    @Test
    void containsIsInclusiveOnBothCorners() {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 2);
        assertTrue(box.contains(new BlockPos(0, 0, 0)));
        assertTrue(box.contains(new BlockPos(2, 2, 2)));
        assertFalse(box.contains(new BlockPos(3, 2, 2)));
        assertFalse(box.contains(new BlockPos(-1, 0, 0)));
    }

    @Test
    void faceTouchingBoxesAreAdjacentButNotIntersecting() {
        BlockBox a = new BlockBox(0, 0, 0, 3, 3, 3);
        BlockBox east = new BlockBox(4, 1, 1, 6, 2, 2);
        BlockBox diagonal = new BlockBox(4, 4, 4, 5, 5, 5);
        BlockBox gap = new BlockBox(5, 0, 0, 6, 3, 3);
        BlockBox overlapping = new BlockBox(3, 0, 0, 5, 3, 3);
        assertTrue(a.touchesFace(east));
        assertTrue(east.touchesFace(a));
        assertFalse(a.touchesFace(diagonal), "corner contact is not a passage");
        assertFalse(a.touchesFace(gap));
        assertFalse(a.touchesFace(overlapping), "overlap is not adjacency");
        assertTrue(a.intersects(overlapping));
    }

    @Test
    void aabbCoversWholeBlocks() {
        BlockBox box = new BlockBox(1, 2, 3, 1, 2, 3);
        assertEquals(1.0, box.toAabb().getXsize());
        assertEquals(2.0, box.toAabb().minY);
        assertEquals(4.0, box.toAabb().maxZ);
    }
}
