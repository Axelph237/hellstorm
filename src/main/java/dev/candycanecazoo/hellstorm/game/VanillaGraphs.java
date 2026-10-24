package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.mob.path.FlowGraph;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds a {@link FlowGraph} by asking the game's walk evaluator ({@link GameNodeEvaluator}, vanilla's with real collision
 * checks) for each node's neighbours, set up for a real mob of the type being guided. It floods out from every cell a
 * mob can stand on in the unlocked zones, as far as the mob can walk within the map's bounds, so the graph holds exactly
 * the cells vanilla navigation can reach. Runs on the server thread (it reads the world).
 */
final class VanillaGraphs {
    /** Exposes the node cache so neighbours can be asked for any cell, not just from a mob's position. */
    private static final class Probe extends GameNodeEvaluator {
        Node at(int x, int y, int z) {
            return getNode(x, y, z);
        }
    }

    private static final int MAX_NODES = 400_000;
    /** How far past the outermost zone a walk may go: a doorway in an outer wall is outside every zone. */
    private static final int MARGIN = 2;
    private static final float DIAGONAL = 1.4142135f;

    private VanillaGraphs() {}

    static FlowGraph build(ServerLevel level, Mob template, GameMap map, Set<ZoneId> unlocked, Predicate<BlockPos> shut) {
        long started = System.nanoTime();
        List<BlockBox> all = map.map().zones().values().stream().flatMap(z -> z.boxes().stream()).toList();
        List<BlockBox> seeds = map.map().zones().values().stream().filter(z -> unlocked.contains(z.id())).flatMap(z -> z.boxes().stream()).toList();
        FlowGraph.Builder graph = FlowGraph.builder();
        if (seeds.isEmpty()) {
            return graph.build();
        }
        BlockBox bounds = bounds(all);
        PathNavigationRegion region = new PathNavigationRegion(level, bounds.min().offset(-MARGIN - 2, -MARGIN - 2, -MARGIN - 2),
                bounds.max().offset(MARGIN + 2, MARGIN + 4, MARGIN + 2));
        Probe probe = new Probe();
        probe.shut(shut);
        probe.setCanPassDoors(true);
        probe.setCanOpenDoors(true);
        probe.setCanFloat(true);
        probe.prepare(region, template);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        try {
            for (BlockBox box : seeds) {
                for (BlockPos cell : BlockPos.betweenClosed(box.min(), box.max())) {
                    if (!shut.test(cell) && standable(probe, region, template, cell) && seen.add(cell.asLong())) {
                        queue.enqueue(cell.asLong());
                    }
                }
            }
            Node[] neighbours = new Node[32];
            while (!queue.isEmpty() && seen.size() < MAX_NODES) {
                BlockPos from = BlockPos.of(queue.dequeueLong());
                graph.node(from);
                int count = probe.getNeighbors(neighbours, probe.at(from.getX(), from.getY(), from.getZ()));
                for (int i = 0; i < count; i++) {
                    Node next = neighbours[i];
                    BlockPos to = new BlockPos(next.x, next.y, next.z);
                    if (!within(bounds, to)) {
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
        Hellstorm.LOG.info("Built a {}-node zombie graph for {} in {} ms", built.nodeCount(),
                template.getType().builtInRegistryHolder().key().location(), (System.nanoTime() - started) / 1_000_000);
        return built;
    }

    private static boolean within(BlockBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() - MARGIN && pos.getX() <= bounds.maxX() + MARGIN
                && pos.getY() >= bounds.minY() - MARGIN && pos.getY() <= bounds.maxY() + MARGIN
                && pos.getZ() >= bounds.minZ() - MARGIN && pos.getZ() <= bounds.maxZ() + MARGIN;
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

    private static BlockBox bounds(List<BlockBox> boxes) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockBox b : boxes) {
            minX = Math.min(minX, b.minX()); minY = Math.min(minY, b.minY()); minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX()); maxY = Math.max(maxY, b.maxY()); maxZ = Math.max(maxZ, b.maxZ());
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
