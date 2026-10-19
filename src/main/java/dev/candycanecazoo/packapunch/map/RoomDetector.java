package dev.candycanecazoo.packapunch.map;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Flood-fills the open space around a start position, for `/pap zone detect`. */
public final class RoomDetector {
    private RoomDetector() {}

    public sealed interface Result permits Found, TooLarge, StartBlocked {}

    public record Found(Set<BlockPos> cells) implements Result {}

    /** The fill reached the volume cap, which usually means the room isn't closed. */
    public record TooLarge(int limit) implements Result {}

    public record StartBlocked() implements Result {}

    public static Result detect(BlockPos start, Predicate<BlockPos> open, int maxVolume) {
        if (!open.test(start)) {
            return new StartBlocked();
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start.asLong());
        queue.enqueue(start.asLong());
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
        return new Found(cells);
    }
}
