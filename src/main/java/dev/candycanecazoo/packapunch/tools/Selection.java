package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.BlockBox;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/** A Zone Wand selection: nothing, a first corner, or both corners. */
public record Selection(Optional<BlockPos> first, Optional<BlockPos> second) {
    public static final Selection NONE = new Selection(Optional.empty(), Optional.empty());

    /** First click sets the first corner, second sets the other, a third starts over. */
    public Selection click(BlockPos pos) {
        if (first.isEmpty() || second.isPresent()) {
            return new Selection(Optional.of(pos.immutable()), Optional.empty());
        }
        return new Selection(first, Optional.of(pos.immutable()));
    }

    public Optional<BlockBox> box() {
        return first.flatMap(a -> second.map(b -> BlockBox.of(a, b)));
    }
}
