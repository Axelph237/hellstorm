package dev.candycanecazoo.packapunch.client.outline;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** The only place that touches Ponder's Outliner. Outlines fade unless shown or kept every client tick. */
public final class ZoneOutlines {
    public static final float THIN = 1 / 64f;
    public static final float THICK = 1 / 16f;

    private ZoneOutlines() {}

    public static void box(Object slot, AABB box, int color, float lineWidth) {
        Outliner.getInstance().showAABB(slot, box).colored(color).lineWidth(lineWidth);
    }

    public static void line(Object slot, Vec3 from, Vec3 to, int color, float lineWidth) {
        Outliner.getInstance().showLine(slot, from, to).colored(color).lineWidth(lineWidth);
    }

    /**
     * Outlines the outer surface of a set of blocks as one shape. Building a cluster outline is costly, so it is built
     * only when the slot is new and kept alive afterwards. Use a slot that changes whenever the blocks change.
     */
    public static void cluster(Object slot, Supplier<? extends Iterable<BlockPos>> blocks, int color, float lineWidth) {
        Outliner outliner = Outliner.getInstance();
        if (outliner.getOutlines().containsKey(slot)) {
            outliner.keep(slot);
        } else {
            outliner.showCluster(slot, blocks.get());
        }
        outliner.edit(slot).ifPresent(params -> params.colored(color).lineWidth(lineWidth));
    }
}
