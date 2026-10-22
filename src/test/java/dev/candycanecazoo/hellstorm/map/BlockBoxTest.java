package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    void overlappingBoxesIntersect() {
        BlockBox a = new BlockBox(0, 0, 0, 3, 3, 3);
        assertTrue(a.intersects(new BlockBox(3, 0, 0, 5, 3, 3)));
        assertFalse(a.intersects(new BlockBox(4, 0, 0, 5, 3, 3)));
    }

    @Test
    void aabbCoversWholeBlocks() {
        BlockBox box = new BlockBox(1, 2, 3, 1, 2, 3);
        assertEquals(1.0, box.toAabb().getXsize());
        assertEquals(2.0, box.toAabb().minY);
        assertEquals(4.0, box.toAabb().maxZ);
    }

    @Test
    void takingABoxOutLeavesExactlyTheOtherBlocksOnce() {
        BlockBox box = new BlockBox(0, 0, 0, 3, 2, 3);
        for (BlockBox cut : List.of(new BlockBox(1, 1, 1, 2, 1, 2), new BlockBox(-5, -5, -5, 1, 9, 9), new BlockBox(2, 0, 0, 2, 2, 3),
                new BlockBox(3, 2, 3, 8, 8, 8), new BlockBox(-1, -1, -1, 4, 3, 4), new BlockBox(5, 0, 0, 6, 2, 3))) {
            Map<BlockPos, Integer> left = new HashMap<>();
            for (BlockBox piece : box.minus(cut)) {
                BlockPos.betweenClosed(piece.min(), piece.max()).forEach(p -> left.merge(p.immutable(), 1, Integer::sum));
            }
            for (BlockPos p : BlockPos.betweenClosed(box.min(), box.max())) {
                assertEquals(cut.contains(p) ? null : 1, left.get(p), "block " + p.toShortString() + " minus " + cut);
            }
            assertTrue(left.keySet().stream().allMatch(box::contains), "nothing outside the box, minus " + cut);
        }
    }
}
