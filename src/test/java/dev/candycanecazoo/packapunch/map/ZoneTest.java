package dev.candycanecazoo.packapunch.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneTest {
    private static final BlockBox ROOM = new BlockBox(0, 0, 0, 9, 3, 9);

    @Test
    void aBoxInsideAnExistingOneAddsNothing() {
        Zone zone = Zone.empty(new ZoneId("hall")).withBox(ROOM);
        assertEquals(List.of(ROOM), zone.withBox(ROOM).withBox(new BlockBox(2, 0, 2, 4, 1, 4)).boxes());
    }

    @Test
    void aBoxCoveringExistingOnesReplacesThem() {
        Zone zone = Zone.empty(new ZoneId("hall")).withBoxes(List.of(new BlockBox(1, 0, 1, 2, 0, 2), new BlockBox(20, 0, 0, 21, 0, 0)));
        assertEquals(List.of(new BlockBox(20, 0, 0, 21, 0, 0), ROOM), zone.withBox(ROOM).boxes());
    }

    @Test
    void partlyOverlappingBoxesAreBothKept() {
        BlockBox wing = new BlockBox(5, 0, 5, 14, 3, 14);
        Zone zone = Zone.empty(new ZoneId("hall")).withBox(ROOM).withBox(wing);
        assertEquals(List.of(ROOM, wing), zone.boxes());
        assertTrue(zone.contains(new net.minecraft.core.BlockPos(12, 0, 12)));
    }
}
