package dev.candycanecazoo.packapunch.client.outline;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** The only place that touches Ponder's Outliner. Outlines fade unless shown again every client tick. */
public final class ZoneOutlines {
    public static final float THIN = 1 / 64f;
    public static final float THICK = 1 / 16f;

    private ZoneOutlines() {}

    public static void box(Object slot, AABB box, int color, float lineWidth) {
        Outliner.getInstance().showAABB(slot, box).colored(color).lineWidth(lineWidth);
    }

    public static void cluster(Object slot, Iterable<BlockPos> blocks, int color, float lineWidth) {
        Outliner.getInstance().showCluster(slot, blocks).colored(color).lineWidth(lineWidth);
    }
}
