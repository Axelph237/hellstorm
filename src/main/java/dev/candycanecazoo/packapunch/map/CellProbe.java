package dev.candycanecazoo.packapunch.map;

import net.minecraft.core.BlockPos;

/** What a mob could do with one cell outside every zone. Keeps passage finding independent of the live world. */
@FunctionalInterface
public interface CellProbe {
    enum Cell { OPEN, CLOSED_DOOR, SOLID }

    CellProbe ALL_SOLID = pos -> Cell.SOLID;

    Cell at(BlockPos pos);
}
