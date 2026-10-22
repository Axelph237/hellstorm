package dev.candycanecazoo.hellstorm.client.outline;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** The map tools' outlines. Outlines fade unless shown or kept every client tick. */
public final class ZoneOutlines {
    public static final float THIN = 1 / 64f;
    public static final float THICK = 1 / 16f;

    private ZoneOutlines() {}

    public static void box(Object slot, AABB box, int color, float lineWidth) {
        Outlines.box(slot, box, color, lineWidth);
    }

    public static void line(Object slot, Vec3 from, Vec3 to, int color, float lineWidth) {
        Outlines.line(slot, from, to, color, lineWidth);
    }

    /**
     * Outlines the outer surface of a set of blocks as one shape. Building a cluster outline is costly, so it is built
     * only when the slot is new and kept alive afterwards. Use a slot that changes whenever the blocks change.
     */
    public static void cluster(Object slot, Supplier<? extends Iterable<BlockPos>> blocks, int color, float lineWidth) {
        if (!Outlines.keep(slot, color, lineWidth)) {
            Outlines.cluster(slot, blocks.get(), color, lineWidth);
        }
    }
}
