package dev.candycanecazoo.packapunch.mob.path;

/** What a mob can do with one block cell. The caller reports cells outside the zone as {@link Cell#SOLID}. */
@FunctionalInterface
public interface CellQuery {
    enum Cell {
        /** A mob's body can occupy it. */
        OPEN,
        /** Blocks movement and can be stood on. */
        SOLID
    }

    Cell at(int x, int y, int z);
}
