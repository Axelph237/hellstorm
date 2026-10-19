package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Predicate;

public final class RoomDetection {
    private RoomDetection() {}

    /**
     * A cell is open for room detection if nothing collides there and no zone already claims it. Windows always close a
     * room, whatever their boards: a bare barricade reports no collision to context-free queries like this one.
     */
    public static Predicate<BlockPos> openCells(ServerLevel level, ZoneIndex index) {
        return pos -> !level.isOutsideBuildHeight(pos)
                && !level.getBlockState(pos).is(PapRegistries.BARRICADE.get())
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && index.zoneAt(pos).isEmpty();
    }
}
