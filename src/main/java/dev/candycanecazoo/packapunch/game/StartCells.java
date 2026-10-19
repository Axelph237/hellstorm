package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Cells in the start zones where a player can stand: solid ground below, two open blocks of headroom. */
public final class StartCells {
    private static final int MAX_CELLS = 4096;

    private StartCells() {}

    public static List<BlockPos> find(ServerLevel level, List<Zone> startZones) {
        List<BlockPos> cells = new ArrayList<>();
        for (Zone zone : startZones) {
            for (BlockBox box : zone.boxes()) {
                for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                    if (standable(level, pos)) {
                        cells.add(pos.immutable());
                        if (cells.size() >= MAX_CELLS) {
                            return cells;
                        }
                    }
                }
            }
        }
        return cells;
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
