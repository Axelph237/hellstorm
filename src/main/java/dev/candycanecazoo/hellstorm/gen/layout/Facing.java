package dev.candycanecazoo.hellstorm.gen.layout;

/** A side of a room on the one floor maps have: which way a connector's opening faces out. Clockwise as seen from above. */
public enum Facing {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx;
    public final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Facing clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Facing opposite() {
        return values()[(ordinal() + 2) % 4];
    }
}
