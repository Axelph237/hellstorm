package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.BlockBox;
import net.minecraft.core.BlockPos;

import java.util.BitSet;

/**
 * An immutable snapshot of where one movement class can stand inside a zone. A node is the footprint's minimum
 * corner: the node at (x, y, z) is walkable when the {@code width x width x height} body starting there is open and
 * every cell under it is solid. Never written after construction, so any thread may read it.
 */
public final class PassabilityGrid {
    private final BlockBox bounds;
    private final MovementClass movementClass;
    /** Every cell a move from a node in {@code bounds} can depend on: the floor row below, footprint overhang, and headroom above. */
    private final BlockBox cells;
    private final BitSet open;
    private final BitSet walkable;

    private PassabilityGrid(BlockBox bounds, MovementClass movementClass, BlockBox cells, BitSet open) {
        this.bounds = bounds;
        this.movementClass = movementClass;
        this.cells = cells;
        this.open = open;
        this.walkable = new BitSet(Math.toIntExact(bounds.volume()));
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    if (clear(x, y, z, movementClass.height()) && solid(x, y - 1, z)) {
                        walkable.set(index(bounds, x, y, z));
                    }
                }
            }
        }
    }

    public static PassabilityGrid build(BlockBox bounds, CellQuery query, MovementClass movementClass) {
        int overhang = movementClass.width() - 1;
        BlockBox cells = new BlockBox(
                bounds.minX(), bounds.minY() - 1, bounds.minZ(),
                bounds.maxX() + overhang, bounds.maxY() + movementClass.height(), bounds.maxZ() + overhang);
        BitSet open = new BitSet(Math.toIntExact(cells.volume()));
        for (int y = cells.minY(); y <= cells.maxY(); y++) {
            for (int z = cells.minZ(); z <= cells.maxZ(); z++) {
                for (int x = cells.minX(); x <= cells.maxX(); x++) {
                    if (query.at(x, y, z) == CellQuery.Cell.OPEN) {
                        open.set(index(cells, x, y, z));
                    }
                }
            }
        }
        return new PassabilityGrid(bounds, movementClass, cells, open);
    }

    public BlockBox bounds() {
        return bounds;
    }

    public MovementClass movementClass() {
        return movementClass;
    }

    public boolean walkable(int x, int y, int z) {
        return bounds.contains(x, y, z) && walkable.get(index(bounds, x, y, z));
    }

    public boolean walkable(BlockPos pos) {
        return walkable(pos.getX(), pos.getY(), pos.getZ());
    }

    /** True when the footprint at (x, z) is open on every layer from {@code y} up through {@code y + layers - 1}. */
    boolean clear(int x, int y, int z, int layers) {
        for (int dy = 0; dy < layers; dy++) {
            for (int dz = 0; dz < movementClass.width(); dz++) {
                for (int dx = 0; dx < movementClass.width(); dx++) {
                    if (!openCell(x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean solid(int x, int y, int z) {
        for (int dz = 0; dz < movementClass.width(); dz++) {
            for (int dx = 0; dx < movementClass.width(); dx++) {
                if (openCell(x + dx, y, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean openCell(int x, int y, int z) {
        return cells.contains(x, y, z) && open.get(index(cells, x, y, z));
    }

    private static int index(BlockBox box, int x, int y, int z) {
        return ((y - box.minY()) * box.sizeZ() + (z - box.minZ())) * box.sizeX() + (x - box.minX());
    }
}
