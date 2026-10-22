package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SelectionTest {
    private static Selection select(BlockBox... boxes) {
        Selection selection = Selection.NONE;
        for (BlockBox box : boxes) {
            selection = selection.click(box.min()).click(box.max());
        }
        return selection;
    }

    @Test
    void twoClicksMakeABoxAndAThirdStartsTheNextWithoutLosingIt() {
        BlockBox first = new BlockBox(0, 0, 0, 3, 2, 3);
        Selection one = select(first);
        assertEquals(List.of(first), one.boxes());
        assertTrue(one.corner().isEmpty());
        Selection pending = one.click(new BlockPos(10, 0, 10));
        assertEquals(List.of(first), pending.boxes(), "starting the next box keeps the first");
        assertEquals(Optional.of(new BlockPos(10, 0, 10)), pending.corner());
    }

    @Test
    void boxesThatMakeOneBoxAreMerged() {
        assertEquals(List.of(new BlockBox(0, 0, 0, 7, 2, 3)), select(new BlockBox(0, 0, 0, 3, 2, 3), new BlockBox(4, 0, 0, 7, 2, 3)).boxes(),
                "side by side and the same across");
        assertEquals(List.of(new BlockBox(0, 0, 0, 5, 2, 3)), select(new BlockBox(0, 0, 0, 3, 2, 3), new BlockBox(2, 0, 0, 5, 2, 3)).boxes(),
                "overlapping and the same across");
        assertEquals(List.of(new BlockBox(0, 0, 0, 3, 2, 3)), select(new BlockBox(0, 0, 0, 3, 2, 3), new BlockBox(1, 1, 1, 2, 2, 2)).boxes(),
                "a box inside another adds nothing");
        assertEquals(List.of(new BlockBox(-1, 0, -1, 4, 3, 4)), select(new BlockBox(0, 0, 0, 3, 2, 3), new BlockBox(-1, 0, -1, 4, 3, 4)).boxes(),
                "a box around another replaces it");
    }

    @Test
    void aMergeThatClosesAGapJoinsAllThree() {
        Selection ends = select(new BlockBox(0, 0, 0, 1, 0, 0), new BlockBox(4, 0, 0, 5, 0, 0));
        assertEquals(2, ends.boxes().size());
        assertEquals(List.of(new BlockBox(0, 0, 0, 5, 0, 0)), select(new BlockBox(0, 0, 0, 1, 0, 0), new BlockBox(4, 0, 0, 5, 0, 0),
                new BlockBox(2, 0, 0, 3, 0, 0)).boxes());
    }

    @Test
    void boxesThatDontMakeOneBoxAreKeptApart() {
        BlockBox hall = new BlockBox(0, 0, 0, 9, 2, 2);
        BlockBox wing = new BlockBox(0, 0, 3, 2, 2, 9);
        Selection ell = select(hall, wing);
        assertEquals(List.of(hall, wing), ell.boxes());
        assertTrue(ell.single().isEmpty());
        assertEquals(Optional.of(new BlockBox(0, 0, 0, 9, 2, 9)), ell.bounds());
    }
}
