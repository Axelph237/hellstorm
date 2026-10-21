package dev.candycanecazoo.hellstorm.command;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A room at x,z 0..9; its north wall is z = -1, its east wall x = 10. */
class WindowCommandsTest {
    private static final ZoneIndex ROOM = new ZoneIndex(List.of(new Zone(new ZoneId("room"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true)));

    @Test
    void theZoneSideIsInsideSoOutwardPointsAway() {
        assertEquals(Optional.of(Direction.NORTH), WindowCommands.outward(new BlockBox(3, 0, -1, 5, 1, -1), ROOM));
        assertEquals(Optional.of(Direction.EAST), WindowCommands.outward(new BlockBox(10, 0, 4, 10, 1, 4), ROOM), "1x2 window in the east wall");
    }

    @Test
    void ambiguousOrThickOpeningsAreRejected() {
        assertEquals(Optional.empty(), WindowCommands.outward(new BlockBox(3, 0, -2, 5, 1, -1), ROOM), "two blocks thick");
        assertEquals(Optional.empty(), WindowCommands.outward(new BlockBox(3, 0, 4, 5, 1, 4), ROOM), "inside the room: zone on both sides");
        assertEquals(Optional.empty(), WindowCommands.outward(new BlockBox(30, 0, 30, 30, 1, 30), ROOM), "no zone on either side");
    }
}
