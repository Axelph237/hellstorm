package dev.candycanecazoo.packapunch.mob.path;

import dev.candycanecazoo.packapunch.map.ZoneId;
import net.minecraft.core.BlockPos;

/**
 * An opening between two zones, walkable in both directions. {@code cellA} is a walkable node in zone A's grid beside
 * the opening and {@code cellB} the same in zone B's. {@code crossingCost} is the walk from one to the other in the
 * search mode's units, and must be at least the heuristic distance between the cells to keep the search optimal.
 */
public record Portal(String id, ZoneId zoneA, BlockPos cellA, ZoneId zoneB, BlockPos cellB, int crossingCost) {}
