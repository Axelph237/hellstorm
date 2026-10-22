package dev.candycanecazoo.hellstorm.map;

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

    @Test
    void cuttingAZoneRemovesTheCutBlocksFromEveryBoxAndKeepsTheRest() {
        BlockBox wing = new BlockBox(10, 0, 0, 14, 3, 9);
        Zone zone = new Zone(new ZoneId("hall"), List.of(ROOM, wing), true).without(new BlockBox(8, 0, 0, 11, 3, 9));
        assertTrue(zone.start());
        assertTrue(zone.contains(new net.minecraft.core.BlockPos(7, 0, 0)));
        assertFalse(zone.contains(new net.minecraft.core.BlockPos(9, 2, 5)));
        assertFalse(zone.contains(new net.minecraft.core.BlockPos(11, 2, 5)));
        assertTrue(zone.contains(new net.minecraft.core.BlockPos(12, 2, 5)));
        assertEquals(ROOM.volume() + wing.volume() - 4L * 4 * 10, zone.volume());
        assertTrue(Zone.empty(new ZoneId("hall")).withBox(ROOM).without(ROOM.grown(1)).boxes().isEmpty());
    }
}
