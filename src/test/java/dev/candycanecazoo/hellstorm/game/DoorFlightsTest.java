package dev.candycanecazoo.hellstorm.game;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorFlightsTest {
    private static List<BlockPos> wall(int width, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int z = 0; z < width; z++) {
            for (int y = 0; y < height; y++) {
                blocks.add(new BlockPos(0, y, z));
            }
        }
        return blocks;
    }

    @Test
    void aDoorwayGoesTopFirstOneStepApart() {
        Map<BlockPos, Integer> delays = DoorFlights.stagger(wall(1, 2));
        assertEquals(Map.of(new BlockPos(0, 1, 0), 0, new BlockPos(0, 0, 0), DoorFlights.STEP_TICKS), delays);
    }

    @Test
    void eachLayerSpreadsFromTheMiddleAndNoBlockGoesBeforeTheOneAboveIt() {
        Map<BlockPos, Integer> delays = DoorFlights.stagger(wall(5, 3));
        assertEquals(0, delays.get(new BlockPos(0, 2, 2)));
        assertTrue(delays.get(new BlockPos(0, 2, 1)) < delays.get(new BlockPos(0, 2, 0)), "the top layer should spread outward: " + delays);
        assertEquals(delays.get(new BlockPos(0, 2, 1)), delays.get(new BlockPos(0, 2, 3)), "the wave should be even on both sides: " + delays);
        delays.forEach((pos, delay) -> {
            Integer above = delays.get(pos.above());
            assertTrue(above == null || above <= delay, pos + " goes before the block above it: " + delays);
        });
    }

    @Test
    void theBiggestDoorStillGoesWithinTheBound() {
        Map<BlockPos, Integer> delays = DoorFlights.stagger(wall(16, 16));
        assertEquals(0, Collections.min(delays.values()));
        assertEquals(DoorFlights.MAX_STAGGER_TICKS, Collections.max(delays.values()));
        delays.forEach((pos, delay) -> {
            Integer above = delays.get(pos.above());
            assertTrue(above == null || above <= delay, pos + " goes before the block above it");
        });
    }
}
