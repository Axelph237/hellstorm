package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.DoorDef;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Turning a Zone Wand selection into a door's blocks, or taking them out again. */
public final class DoorBlocks {
    public static final int MAX = 256;

    /** The door after the edit, how many blocks it gained or lost, and how many it skipped (other doors, over {@link #MAX}). */
    public record Result(DoorDef door, int changed, int skipped) {}

    private DoorBlocks() {}

    /** Adds every block in {@code box} that isn't air and isn't part of another door, up to {@link #MAX}. */
    public static Result add(DoorDef door, BlockBox box, Predicate<BlockPos> solid, Function<BlockPos, Optional<String>> doorAt) {
        String id = door.id();
        int added = 0;
        int skipped = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (!solid.test(pos) || door.members().contains(pos)) {
                continue;
            }
            if (door.members().size() >= MAX || doorAt.apply(pos).filter(other -> !other.equals(id)).isPresent()) {
                skipped++;
                continue;
            }
            door = door.withMember(pos);
            added++;
        }
        return new Result(door, added, skipped);
    }

    /** Takes every block in {@code box} out of the door. */
    public static Result cut(DoorDef door, BlockBox box) {
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (door.members().contains(pos)) {
                door = door.withoutMember(pos);
                removed++;
            }
        }
        return new Result(door, removed, 0);
    }
}
