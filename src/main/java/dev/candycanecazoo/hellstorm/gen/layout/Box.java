package dev.candycanecazoo.hellstorm.gen.layout;

/** A box of whole blocks, both corners included. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("A box's min is past its max: " + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ);
        }
    }

    /** The box from one corner to the other, in either order. */
    public static Box between(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Whether they share a block. Boxes that only touch, side to side, don't. */
    public boolean intersects(Box other) {
        return minX <= other.maxX && other.minX <= maxX && minY <= other.maxY && other.minY <= maxY && minZ <= other.maxZ && other.minZ <= maxZ;
    }

    public Box moved(int dx, int dy, int dz) {
        return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /** One block out the way {@code facing} looks. */
    public Box stepped(Facing facing) {
        return moved(facing.dx, 0, facing.dz);
    }

    public boolean sameSize(Box other) {
        return sizeX() == other.sizeX() && sizeY() == other.sizeY() && sizeZ() == other.sizeZ();
    }
}
