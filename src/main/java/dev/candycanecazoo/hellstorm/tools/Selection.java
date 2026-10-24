package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Zone Wand selection: the boxes picked so far, and the first corner of the next one. Every two clicks add a box, and
 * a box that makes one box with another (beside it and the same size across, inside it, or around it) is merged into it.
 * Only clearing it ({@code /hs zones desel} or {@code /hs zones cancel}) takes boxes away.
 */
public record Selection(List<BlockBox> boxes, Optional<BlockPos> corner) {
    public static final Selection NONE = new Selection(List.of(), Optional.empty());

    public Selection {
        boxes = List.copyOf(boxes);
    }

    /** The first click of a pair sets a corner, the second adds the box between them. */
    public Selection click(BlockPos pos) {
        if (corner.isEmpty()) {
            return new Selection(boxes, Optional.of(pos.immutable()));
        }
        return new Selection(withBox(boxes, BlockBox.of(corner.get(), pos)), Optional.empty());
    }

    static List<BlockBox> withBox(List<BlockBox> boxes, BlockBox added) {
        List<BlockBox> next = new ArrayList<>(boxes);
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < next.size(); i++) {
                BlockBox box = next.get(i);
                if (box.contains(added)) {
                    return next;
                }
                Optional<BlockBox> union = box.exactUnion(added);
                if (union.isPresent()) {
                    next.remove(i);
                    added = union.get();
                    merged = true;
                    break;
                }
            }
        }
        next.add(added);
        return next;
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    /** The one box selected, if the selection is exactly one. */
    public Optional<BlockBox> single() {
        return boxes.size() == 1 ? Optional.of(boxes.get(0)) : Optional.empty();
    }

    /** The smallest box holding the whole selection. */
    public Optional<BlockBox> bounds() {
        return boxes.stream().reduce(BlockBox::union);
    }
}
