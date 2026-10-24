package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;

/** What a mob could do with one cell. Keeps passage finding independent of the live world. */
@FunctionalInterface
public interface CellProbe {
    /** {@code TALL} is solid and higher than a block, such as a fence or a wall block: nothing stands on it or jumps it. */
    enum Cell { OPEN, CLOSED_DOOR, SOLID, TALL }

    CellProbe ALL_SOLID = pos -> Cell.SOLID;

    /** A stand-in for a world nobody has looked at: zones are the walkable space their maker drew, and the rest is solid. */
    static CellProbe zonesOnly(ZoneIndex index) {
        return pos -> index.zoneAt(pos).isPresent() ? Cell.OPEN : Cell.SOLID;
    }

    Cell at(BlockPos pos);
}
