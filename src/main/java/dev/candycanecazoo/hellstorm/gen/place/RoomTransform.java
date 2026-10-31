package dev.candycanecazoo.hellstorm.gen.place;

import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Facing;
import dev.candycanecazoo.hellstorm.gen.layout.Rotation;
import dev.candycanecazoo.hellstorm.gen.layout.Size;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Where a room's blocks and map land once it's turned ({@link Rotation}, put back at its corner) and its corner moved to
 * {@code corner}: the same transform the layout used, so the blocks, the connectors and the map all agree.
 */
public record RoomTransform(Rotation rotation, Size size, BlockPos corner) {
    public BlockPos apply(int x, int y, int z) {
        int[] p = rotation.point(x, z, size);
        return corner.offset(p[0], y, p[1]);
    }

    public BlockPos apply(BlockPos local) {
        return apply(local.getX(), local.getY(), local.getZ());
    }

    public BlockBox apply(BlockBox box) {
        return BlockBox.of(apply(box.minX(), box.minY(), box.minZ()), apply(box.maxX(), box.maxY(), box.maxZ()));
    }

    public BlockBox apply(Box box) {
        return BlockBox.of(apply(box.minX(), box.minY(), box.minZ()), apply(box.maxX(), box.maxY(), box.maxZ()));
    }

    public Direction apply(Direction direction) {
        return direction.getAxis().isHorizontal() ? direction(rotation.apply(facing(direction))) : direction;
    }

    public Direction apply(Facing facing) {
        return direction(rotation.apply(facing));
    }

    public static Facing facing(Direction direction) {
        return switch (direction) {
            case EAST -> Facing.EAST;
            case SOUTH -> Facing.SOUTH;
            case WEST -> Facing.WEST;
            default -> Facing.NORTH;
        };
    }

    public static Direction direction(Facing facing) {
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }

    /**
     * Where Minecraft's {@code StructureTemplate} must be placed, turned by {@link #minecraftRotation()} about its own
     * 0, 0, 0, for the room to land with its corner at {@link #corner}: Minecraft turns a block at {@code x, z} to
     * {@code -z, x} on a clockwise quarter, without putting the room back at its corner.
     */
    public BlockPos templatePosition() {
        int[] zero = rotation.point(0, 0, size);
        return corner.offset(zero[0], 0, zero[1]);
    }

    public net.minecraft.world.level.block.Rotation minecraftRotation() {
        return switch (rotation) {
            case NONE -> net.minecraft.world.level.block.Rotation.NONE;
            case CLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
        };
    }

    /**
     * The room's slice of the map, turned and moved into place, every id given {@code prefix} so rooms never share one.
     * Its zones are start zones only if {@code start}.
     */
    public ZombiesMap apply(ZombiesMap slice, String prefix, boolean start) {
        Map<ZoneId, Zone> zones = new TreeMap<>();
        slice.zones().forEach((id, zone) -> {
            ZoneId moved = zoneId(prefix, id);
            zones.put(moved, new Zone(moved, zone.boxes().stream().map(this::apply).toList(), start));
        });
        Map<BlockPos, FeatureKind> features = new LinkedHashMap<>();
        slice.features().forEach((pos, kind) -> features.put(apply(pos), kind));
        Map<String, WindowDef> windows = new TreeMap<>();
        slice.windows().forEach((id, w) -> windows.put(prefix + "_" + id, new WindowDef(prefix + "_" + id, apply(w.opening()), apply(w.outward()), w.maxBoards())));
        Map<String, DoorDef> doors = new TreeMap<>();
        slice.doors().forEach((id, d) -> doors.put(prefix + "_" + id, new DoorDef(prefix + "_" + id,
                d.members().stream().map(this::apply).collect(Collectors.toSet()), d.cost(), d.requiresPower(), d.style())));
        return new ZombiesMap(zones, features, windows, doors);
    }

    /** {@code prefix_id}, cut to a zone id's 32 characters with a hash of the rest so two never collide. */
    static ZoneId zoneId(String prefix, ZoneId id) {
        String full = prefix + "_" + id.value();
        if (full.length() <= 32) {
            return new ZoneId(full);
        }
        String hash = Integer.toHexString(full.hashCode() & 0x7fffffff);
        return new ZoneId(full.substring(0, 32 - hash.length() - 1) + "_" + hash);
    }
}
