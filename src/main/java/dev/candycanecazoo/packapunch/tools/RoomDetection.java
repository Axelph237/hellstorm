package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.ZoneIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Predicate;

public final class RoomDetection {
    private RoomDetection() {}

    /** A cell is open for room detection if nothing collides there and no zone already claims it. */
    public static Predicate<BlockPos> openCells(ServerLevel level, ZoneIndex index) {
        return pos -> !level.isOutsideBuildHeight(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && index.zoneAt(pos).isEmpty();
    }
}
