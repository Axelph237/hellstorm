package dev.candycanecazoo.hellstorm.gen.layout;

/**
 * A room turned by quarter turns about the vertical axis, clockwise as seen from above, then put back with its corner at
 * 0, 0, 0. A room {@code sizeX} by {@code sizeZ} turned once is {@code sizeZ} by {@code sizeX}, and what was its north
 * side faces east.
 */
public enum Rotation {
    NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

    public Facing apply(Facing facing) {
        Facing out = facing;
        for (int i = 0; i < ordinal(); i++) {
            out = out.clockwise();
        }
        return out;
    }

    /** The room's size once turned: its two sides swap on a quarter turn. */
    public Size apply(Size size) {
        return ordinal() % 2 == 0 ? size : new Size(size.z(), size.y(), size.x());
    }

    /** Where a box inside a room of {@code size} ends up once the room is turned. */
    public Box apply(Box box, Size size) {
        int[] a = point(box.minX(), box.minZ(), size);
        int[] b = point(box.maxX(), box.maxZ(), size);
        return Box.between(a[0], box.minY(), a[1], b[0], box.maxY(), b[1]);
    }

    /** Where block {@code x}, {@code z} of a room of {@code size} ends up once the room is turned. */
    public int[] point(int x, int z, Size size) {
        return switch (this) {
            case NONE -> new int[] {x, z};
            case CLOCKWISE_90 -> new int[] {size.z() - 1 - z, x};
            case CLOCKWISE_180 -> new int[] {size.x() - 1 - x, size.z() - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[] {z, size.x() - 1 - x};
        };
    }
}
