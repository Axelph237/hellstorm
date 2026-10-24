package dev.candycanecazoo.hellstorm.map;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Flood-fills the open space around a start position, for `/hs zones detect`. */
public final class RoomDetector {
    private RoomDetector() {}

    public sealed interface Result permits Found, TooLarge, StartBlocked {}

    public record Found(Set<BlockPos> cells) implements Result {}

    /** The fill reached the volume cap, which usually means the room isn't closed. */
    public record TooLarge(int limit) implements Result {}

    public record StartBlocked() implements Result {}

    /**
     * Fills the {@code open} cells joined to {@code start}. {@code floor} cells are low blocks walked on, such as carpets,
     * slabs and stairs: one right under the fill joins the room, so someone standing on it stands in the room, but the fill
     * never spreads from it. Starting in one starts from the cell above.
     */
    public static Result detect(BlockPos start, Predicate<BlockPos> open, Predicate<BlockPos> floor, int maxVolume) {
        BlockPos from = !open.test(start) && floor.test(start) ? start.above() : start;
        if (!open.test(from)) {
            return new StartBlocked();
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(from.asLong());
        queue.enqueue(from.asLong());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            for (Direction direction : Direction.values()) {
                cursor.set(current).move(direction);
                long next = cursor.asLong();
                if (!seen.contains(next) && open.test(cursor)) {
                    if (seen.size() >= maxVolume) {
                        return new TooLarge(maxVolume);
                    }
                    seen.add(next);
                    queue.enqueue(next);
                }
            }
        }
        Set<BlockPos> cells = new HashSet<>(seen.size());
        seen.forEach(l -> cells.add(BlockPos.of(l)));
        for (long l : seen.toLongArray()) {
            cursor.set(l).move(Direction.DOWN);
            if (!seen.contains(cursor.asLong()) && floor.test(cursor)) {
                if (cells.size() >= maxVolume) {
                    return new TooLarge(maxVolume);
                }
                cells.add(cursor.immutable());
            }
        }
        return new Found(cells);
    }
}
