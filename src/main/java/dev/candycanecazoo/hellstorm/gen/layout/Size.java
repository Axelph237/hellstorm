package dev.candycanecazoo.hellstorm.gen.layout;

/** A room's size in blocks. */
public record Size(int x, int y, int z) {
    public Size {
        if (x < 1 || y < 1 || z < 1) {
            throw new IllegalArgumentException("A room is at least a block each way, not " + x + " x " + y + " x " + z);
        }
    }

    /** The room's own box, from 0, 0, 0. */
    public Box box() {
        return new Box(0, 0, 0, x - 1, y - 1, z - 1);
    }
}
