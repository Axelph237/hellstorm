package dev.candycanecazoo.hellstorm.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** A 10x4x10 room at x,z 0..9 with a 3-wide window in its north wall (z = -1), opening north. */
class SpawnLinksTest {
    private static final ZoneId ROOM = new ZoneId("room");
    private static final WindowDef NORTH = new WindowDef("north", new BlockBox(3, 0, -1, 5, 1, -1), Direction.NORTH, 6, ROOM);

    private static ZombiesMap map(BlockPos... markers) {
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(ROOM, List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true)).withWindow(NORTH);
        for (BlockPos marker : markers) {
            map = map.withFeature(marker, FeatureKind.SPAWN);
        }
        return map;
    }

    @Test
    void aMarkerInsideAZoneFeedsThatZoneDirectly() {
        BlockPos inside = new BlockPos(4, 0, 4);
        assertEquals(new SpawnLinks.Link(ROOM, Optional.empty()), SpawnLinks.resolve(map(inside), 6).get(inside));
    }

    @Test
    void aMarkerInFrontOfAWindowLinksToItAndFeedsItsZone() {
        BlockPos outside = new BlockPos(4, 0, -4);
        assertEquals(new SpawnLinks.Link(ROOM, Optional.of("north")), SpawnLinks.resolve(map(outside), 6).get(outside));
    }

    @Test
    void markersBehindTheWallOrOutOfRangeAreNotLinked() {
        BlockPos farAway = new BlockPos(4, 0, -20);
        BlockPos besideTheRoom = new BlockPos(12, 0, 4);
        Map<BlockPos, SpawnLinks.Link> links = SpawnLinks.resolve(map(farAway, besideTheRoom), 6);
        assertFalse(links.containsKey(farAway), "beyond link range");
        assertFalse(links.containsKey(besideTheRoom), "not on the window's outer side");
    }

    /**
     * The window was added while one zone covered the whole room, and the back of the room was later redrawn as a zone of
     * its own. The window opens into the back zone now, so its markers feed that zone, not the one it was made in.
     */
    @Test
    void aWindowFeedsTheZoneBehindItNotTheOneItWasMadeIn() {
        ZoneId back = new ZoneId("back");
        BlockPos outside = new BlockPos(4, 0, -4);
        ZombiesMap redrawn = map(outside)
                .withZone(new Zone(ROOM, List.of(new BlockBox(0, 0, 5, 9, 3, 9)), true))
                .withZone(new Zone(back, List.of(new BlockBox(0, 0, 0, 9, 3, 4)), false));
        assertEquals(new SpawnLinks.Link(back, Optional.of("north")), SpawnLinks.resolve(redrawn, 6).get(outside));
    }

    @Test
    void aWindowWithNoZoneBehindItFeedsNothing() {
        BlockPos outside = new BlockPos(4, 0, -4);
        ZombiesMap moved = map(outside).withZone(new Zone(ROOM, List.of(new BlockBox(0, 0, 5, 9, 3, 9)), true));
        assertFalse(SpawnLinks.resolve(moved, 6).containsKey(outside));
    }

    @Test
    void theNearestFacingWindowWins() {
        WindowDef farther = new WindowDef("farther", new BlockBox(8, 0, -1, 8, 1, -1), Direction.NORTH, 6, ROOM);
        BlockPos marker = new BlockPos(4, 0, -3);
        assertEquals(Optional.of("north"), SpawnLinks.resolve(map(marker).withWindow(farther), 10).get(marker).window());
    }

    @Test
    void windowSizeIsMeasuredAlongTheWall() {
        assertEquals(3, NORTH.width());
        assertEquals(2, NORTH.height());
        WindowDef east = new WindowDef("east", new BlockBox(10, 0, 2, 10, 2, 3), Direction.EAST, 6, ROOM);
        assertEquals(2, east.width());
        assertEquals(new BlockPos(11, 0, 2), east.attackSpot());
        assertEquals(new BlockPos(9, 0, 2), east.insideSpot());
    }
}
