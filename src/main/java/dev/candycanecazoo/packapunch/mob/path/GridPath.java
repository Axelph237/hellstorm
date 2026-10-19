package dev.candycanecazoo.packapunch.mob.path;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Nodes from start to goal inclusive, the total cost in the search mode's units, and how many nodes the search expanded. */
public record GridPath(List<BlockPos> nodes, int cost, int expanded) {
    public GridPath {
        nodes = List.copyOf(nodes);
    }
}
