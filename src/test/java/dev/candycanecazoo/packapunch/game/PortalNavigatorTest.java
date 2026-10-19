package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.ZoneId;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalNavigatorTest {
    private static final ZoneId WEST = new ZoneId("west");
    private static final ZoneId EAST = new ZoneId("east");

    /** West is x < 5, the doorway cell x = 5 belongs to no zone, east is x > 5. */
    private static Optional<ZoneId> zoneAt(BlockPos p) {
        return p.getX() < 5 ? Optional.of(WEST) : p.getX() > 5 ? Optional.of(EAST) : Optional.empty();
    }

    private static List<BlockPos> walk(int fromX, int toX) {
        return java.util.stream.IntStream.rangeClosed(fromX, toX).mapToObj(x -> new BlockPos(x, 0, 2)).toList();
    }

    @Test
    void theNextWaypointIsTheDoorwayNotTheWholePath() {
        assertEquals(Optional.of(new BlockPos(5, 0, 2)), PortalNavigator.nextDoorway(walk(0, 9), WEST, PortalNavigatorTest::zoneAt));
    }

    @Test
    void touchingZonesMakeTheFirstCellOfTheNextZoneTheWaypoint() {
        List<BlockPos> path = List.of(new BlockPos(3, 0, 2), new BlockPos(4, 0, 2), new BlockPos(6, 0, 2));
        assertEquals(Optional.of(new BlockPos(6, 0, 2)), PortalNavigator.nextDoorway(path, WEST, PortalNavigatorTest::zoneAt));
    }

    @Test
    void aPathThatNeverLeavesTheZoneHasNoWaypoint() {
        assertEquals(Optional.empty(), PortalNavigator.nextDoorway(walk(0, 4), WEST, PortalNavigatorTest::zoneAt));
    }
}
