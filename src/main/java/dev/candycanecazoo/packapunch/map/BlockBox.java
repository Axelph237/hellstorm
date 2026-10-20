package dev.candycanecazoo.packapunch.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** An axis-aligned box of whole blocks. Both corners are inclusive, and {@code min <= max} on every axis. */
public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final Codec<BlockBox> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("min").forGetter(BlockBox::min),
            BlockPos.CODEC.fieldOf("max").forGetter(BlockBox::max)
    ).apply(i, BlockBox::of));

    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Box corners out of order: " + minX + "," + minY + "," + minZ + " / " + maxX + "," + maxY + "," + maxZ);
        }
    }

    /** The box spanning two corners in any order. */
    public static BlockBox of(BlockPos a, BlockPos b) {
        return new BlockBox(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static BlockBox single(BlockPos pos) {
        return of(pos, pos);
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
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

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(BlockBox o) {
        return contains(o.minX, o.minY, o.minZ) && contains(o.maxX, o.maxY, o.maxZ);
    }

    public BlockBox moved(int dx, int dy, int dz) {
        return new BlockBox(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /** The smallest box holding both. */
    public BlockBox union(BlockBox o) {
        return new BlockBox(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }

    public BlockBox grown(int by) {
        return new BlockBox(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by);
    }

    public boolean intersects(BlockBox o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public AABB toAabb() {
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public BlockPos center() {
        return new BlockPos((minX + maxX) >> 1, (minY + maxY) >> 1, (minZ + maxZ) >> 1);
    }
}
