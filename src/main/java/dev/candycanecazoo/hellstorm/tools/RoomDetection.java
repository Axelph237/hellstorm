package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** What {@code /hs zones detect} fills, for {@link dev.candycanecazoo.hellstorm.map.RoomDetector}. */
public final class RoomDetection {
    private RoomDetection() {}

    /**
     * A cell is open for room detection if nothing collides there and the map doesn't already use it for a room or a door.
     * Windows always close a room, whatever their boards: a bare barricade reports no collision to context-free queries
     * like this one. So does a door of cobwebs or vines, which collide with nothing either.
     */
    public static Predicate<BlockPos> openCells(ServerLevel level, ZombiesMap map) {
        Predicate<BlockPos> unclaimed = unclaimed(level, map);
        return pos -> unclaimed.test(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * A low block walked on, such as a carpet, slab, stair, path or farmland: it collides, rests on the whole floor below it,
     * and rises no higher than a block without filling its top. A ceiling's top slab and a full block aren't.
     */
    public static Predicate<BlockPos> floorCells(ServerLevel level, ZombiesMap map) {
        Predicate<BlockPos> unclaimed = unclaimed(level, map);
        return pos -> unclaimed.test(pos) && isFloor(level.getBlockState(pos).getCollisionShape(level, pos));
    }

    static boolean isFloor(VoxelShape shape) {
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0
                && Block.isFaceFull(shape, Direction.DOWN) && !Block.isFaceFull(shape, Direction.UP);
    }

    private static Predicate<BlockPos> unclaimed(ServerLevel level, ZombiesMap map) {
        ZoneIndex index = ZoneIndex.of(map);
        Set<BlockPos> doors = new HashSet<>();
        map.doors().values().stream().map(DoorDef::members).forEach(doors::addAll);
        return pos -> !level.isOutsideBuildHeight(pos) && !doors.contains(pos) && index.zoneAt(pos).isEmpty()
                && !level.getBlockState(pos).is(PapRegistries.BARRICADE.get());
    }
}
