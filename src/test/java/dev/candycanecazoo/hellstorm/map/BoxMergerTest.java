package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BoxMergerTest {
    private static Set<BlockPos> cuboid(int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<BlockPos> cells = new HashSet<>();
        BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1).forEach(p -> cells.add(p.immutable()));
        return cells;
    }

    private static void assertExactCover(Set<BlockPos> cells, List<BlockBox> boxes) {
        Set<BlockPos> covered = new HashSet<>();
        for (BlockBox box : boxes) {
            BlockPos.betweenClosed(box.min(), box.max()).forEach(p -> {
                BlockPos cell = p.immutable();
                assertTrue(cells.contains(cell), "box covers a cell outside the input: " + cell);
                assertTrue(covered.add(cell), "boxes overlap at " + cell);
            });
        }
        assertEquals(cells, covered, "some input cells are not covered");
    }

    @Test
    void aSolidRoomIsOneBox() {
        Set<BlockPos> cells = cuboid(0, 64, 0, 9, 67, 5);
        List<BlockBox> boxes = BoxMerger.merge(cells);
        assertEquals(List.of(new BlockBox(0, 64, 0, 9, 67, 5)), boxes);
    }

    @Test
    void anLShapedRoomIsCoveredExactlyWithoutOverlap() {
        Set<BlockPos> cells = cuboid(0, 0, 0, 9, 2, 2);
        cells.addAll(cuboid(0, 0, 3, 2, 2, 9));
        List<BlockBox> boxes = BoxMerger.merge(cells);
        assertExactCover(cells, boxes);
        assertTrue(boxes.size() <= 2, "L-shape should merge into at most 2 boxes, got " + boxes.size());
    }

    @Test
    void aRoomWithAPillarIsCoveredExactly() {
        Set<BlockPos> cells = cuboid(0, 0, 0, 8, 3, 8);
        cells.removeAll(cuboid(4, 0, 4, 4, 3, 4));
        List<BlockBox> boxes = BoxMerger.merge(cells);
        assertExactCover(cells, boxes);
        assertTrue(boxes.size() <= 4, "pillar room should merge into at most 4 boxes, got " + boxes.size());
    }
}
