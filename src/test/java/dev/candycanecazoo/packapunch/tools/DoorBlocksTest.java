package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.DoorDef;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoorBlocksTest {
    private static final BlockBox WALL = new BlockBox(0, 0, 0, 2, 1, 0);

    @Test
    void addsTheSolidBlocksInTheBoxAndSkipsOtherDoors() {
        Set<BlockPos> air = Set.of(new BlockPos(1, 0, 0));
        BlockPos taken = new BlockPos(2, 1, 0);
        DoorBlocks.Result result = DoorBlocks.add(DoorDef.empty("gate", 750), WALL, pos -> !air.contains(pos),
                pos -> pos.equals(taken) ? Optional.of("other") : Optional.empty());
        assertEquals(4, result.changed());
        assertEquals(1, result.skipped());
        assertEquals(Set.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(0, 1, 0), new BlockPos(1, 1, 0)), result.door().members());
    }

    @Test
    void addingAgainChangesNothingAndItsOwnBlocksArentSkipped() {
        DoorDef door = DoorBlocks.add(DoorDef.empty("gate", 750), WALL, pos -> true, pos -> Optional.empty()).door();
        DoorBlocks.Result again = DoorBlocks.add(door, WALL, pos -> true, pos -> Optional.of("gate"));
        assertEquals(0, again.changed());
        assertEquals(0, again.skipped());
        assertEquals(door, again.door());
    }

    @Test
    void stopsAtTheBlockLimit() {
        BlockBox big = new BlockBox(0, 0, 0, 19, 19, 0);
        DoorBlocks.Result result = DoorBlocks.add(DoorDef.empty("gate", 750), big, pos -> true, pos -> Optional.empty());
        assertEquals(DoorBlocks.MAX, result.door().members().size());
        assertEquals(400 - DoorBlocks.MAX, result.skipped());
    }

    @Test
    void cutTakesOutOnlyTheBlocksInTheBox() {
        DoorDef door = DoorBlocks.add(DoorDef.empty("gate", 750), WALL, pos -> true, pos -> Optional.empty()).door();
        DoorBlocks.Result result = DoorBlocks.cut(door, new BlockBox(0, 0, 0, 0, 1, 0));
        assertEquals(2, result.changed());
        assertEquals(4, result.door().members().size());
    }
}
