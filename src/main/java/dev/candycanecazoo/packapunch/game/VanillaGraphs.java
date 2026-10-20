package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.mob.path.FlowGraph;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds a {@link FlowGraph} by asking vanilla's walk evaluator for each node's neighbours, set up for a real mob of the
 * type being steered. Doors, trapdoors, panes, fences, slabs, carpet, and modded blocks therefore behave exactly as vanilla
 * pathfinding sees them, including that mob's size and its pathfinding penalties. Runs on the server thread (it reads the world).
 */
final class VanillaGraphs {
    /** Exposes vanilla's node cache so neighbours can be asked for any cell, not just from a mob's position. */
    private static final class Probe extends WalkNodeEvaluator {
        Node at(int x, int y, int z) {
            return getNode(x, y, z);
        }
    }

    private static final int MAX_NODES = 400_000;
    private static final float DIAGONAL = 1.4142135f;

    private VanillaGraphs() {}

    static FlowGraph build(ServerLevel level, Mob template, GameMap map, Passages.Result passages) {
        long started = System.nanoTime();
        Set<ZoneId> zones = map.unlocked();
        Set<BlockPos> doorways = new HashSet<>();
        passages.gaps().forEach((link, cells) -> {
            if (zones.contains(link.a()) && zones.contains(link.b())) {
                doorways.addAll(cells);
            }
        });
        List<BlockBox> boxes = map.map().zones().values().stream().filter(z -> zones.contains(z.id())).flatMap(z -> z.boxes().stream()).toList();
        FlowGraph.Builder graph = FlowGraph.builder();
        if (boxes.isEmpty()) {
            return graph.build();
        }
        Predicate<BlockPos> inside = pos -> map.index().zoneAt(pos).map(zones::contains).orElse(false) || doorways.contains(pos);
        BlockBox bounds = bounds(boxes, doorways);
        PathNavigationRegion region = new PathNavigationRegion(level, bounds.min().offset(-2, -2, -2), bounds.max().offset(2, 4, 2));
        Probe probe = new Probe();
        probe.setCanPassDoors(true);
        probe.setCanOpenDoors(true);
        probe.setCanFloat(true);
        probe.prepare(region, template);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        try {
            for (BlockBox box : boxes) {
                for (BlockPos cell : BlockPos.betweenClosed(box.min(), box.max())) {
                    if (standable(probe, region, template, cell) && seen.add(cell.asLong())) {
                        queue.enqueue(cell.asLong());
                    }
                }
            }
            Node[] neighbours = new Node[32];
            while (!queue.isEmpty() && seen.size() < MAX_NODES) {
                BlockPos from = BlockPos.of(queue.dequeueLong());
                graph.node(from);
                Node node = probe.at(from.getX(), from.getY(), from.getZ());
                int count = probe.getNeighbors(neighbours, node);
                for (int i = 0; i < count; i++) {
                    Node next = neighbours[i];
                    BlockPos to = new BlockPos(next.x, next.y, next.z);
                    if (!inside.test(to)) {
                        continue;
                    }
                    graph.move(from, to, cost(from, to, next.costMalus));
                    if (seen.add(to.asLong())) {
                        queue.enqueue(to.asLong());
                    }
                }
            }
        } finally {
            probe.done();
        }
        FlowGraph built = graph.build();
        PackAPunch.LOG.info("Built a {}-node zombie graph for {} in {} ms", built.nodeCount(),
                template.getType().builtInRegistryHolder().key().location(), (System.nanoTime() - started) / 1_000_000);
        return built;
    }

    /** Vanilla would let this mob stand here: it isn't blocked, isn't mid-air, and isn't something the mob refuses. */
    private static boolean standable(Probe probe, PathNavigationRegion region, Mob mob, BlockPos cell) {
        BlockPathTypes type = probe.getBlockPathType(region, cell.getX(), cell.getY(), cell.getZ(), mob);
        return type != BlockPathTypes.BLOCKED && type != BlockPathTypes.OPEN && mob.getPathfindingMalus(type) >= 0;
    }

    /** In tenths of a block: 10 per straight block, 14 diagonally, 10 per block of height, plus vanilla's penalty. */
    private static int cost(BlockPos from, BlockPos to, float malus) {
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        float horizontal = dx != 0 && dz != 0 ? DIAGONAL : dx + dz;
        return Math.max(1, Math.round(10 * (horizontal + Math.abs(to.getY() - from.getY()) + Math.max(0, malus))));
    }

    private static BlockBox bounds(List<BlockBox> boxes, Set<BlockPos> extra) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockBox b : boxes) {
            minX = Math.min(minX, b.minX()); minY = Math.min(minY, b.minY()); minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX()); maxY = Math.max(maxY, b.maxY()); maxZ = Math.max(maxZ, b.maxZ());
        }
        for (BlockPos p : extra) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
