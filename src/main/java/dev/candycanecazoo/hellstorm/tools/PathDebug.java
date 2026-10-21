package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.CellProbe;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.mob.path.CellQuery;
import dev.candycanecazoo.hellstorm.mob.path.MovementClass;
import dev.candycanecazoo.hellstorm.mob.path.Portal;
import dev.candycanecazoo.hellstorm.mob.path.ZonePathing;
import dev.candycanecazoo.hellstorm.net.DebugPathsPacket;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code show_paths} debug view: zombies' current paths and the doorways the pathfinder uses, sent to players holding a
 * map tool. Also backs {@code /hs map paths}.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PathDebug {
    /** A normal zombie, the size the doorway view is computed for. */
    public static final MovementClass ZOMBIE = MovementClass.ofHitbox(0.6f, 1.95f);
    private static final int SEND_INTERVAL = 10;
    private static final int MAX_NODES = 64;

    private record CachedPortals(ZombiesMap map, List<Portal> portals) {}

    private static final Map<ResourceKey<Level>, CachedPortals> NO_GAME_PORTALS = new HashMap<>();

    private PathDebug() {}

    /** The portals for a normal zombie on this level's map, read from the world now. */
    public static List<Portal> portals(ServerLevel level, ZombiesMap map) {
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(level), ServerConfig.MAX_PASSAGE_GAP.get());
        CellQuery world = (x, y, z) -> LevelCellProbe.classify(level, new BlockPos(x, y, z)) == CellProbe.Cell.OPEN
                ? CellQuery.Cell.OPEN : CellQuery.Cell.SOLID;
        return ZonePathing.portals(passages, ZonePathing.grids(map, passages, world, ZOMBIE));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % SEND_INTERVAL != 0
                || !DebugRules.get(event.getServer()).get(DebugRules.Rule.SHOW_PATHS)) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<ServerPlayer> viewers = level.players().stream().filter(p -> p.getMainHandItem().getItem() instanceof MapTool).toList();
            if (!viewers.isEmpty()) {
                DebugPathsPacket packet = new DebugPathsPacket(mobPaths(level), doorways(level));
                viewers.forEach(p -> PapNetwork.send(p, packet));
            }
        }
    }

    private static List<DebugPathsPacket.MobPath> mobPaths(ServerLevel level) {
        List<DebugPathsPacket.MobPath> paths = new ArrayList<>();
        GameManager.get(level).ifPresent(active -> {
            for (UUID id : active.game().mobs()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof Mob mob && flowTrail(active.pathing(), mob).map(t -> paths.add(new DebugPathsPacket.MobPath(mob.getId(), t))).isPresent()) {
                    continue;
                }
                if (entity instanceof Mob mob && mob.getNavigation().getPath() != null) {
                    Path path = mob.getNavigation().getPath();
                    List<BlockPos> nodes = new ArrayList<>();
                    nodes.add(mob.blockPosition());
                    for (int i = path.getNextNodeIndex(); i < path.getNodeCount() && nodes.size() < MAX_NODES; i++) {
                        nodes.add(path.getNodePos(i));
                    }
                    paths.add(new DebugPathsPacket.MobPath(mob.getId(), nodes));
                }
            }
        });
        return paths;
    }

    /** The downhill walk a flow-field zombie is following toward its target, from where it stands. */
    private static java.util.Optional<List<BlockPos>> flowTrail(dev.candycanecazoo.hellstorm.game.GamePathing pathing, Mob mob) {
        if (mob.getTarget() == null) {
            return java.util.Optional.empty();
        }
        if (!dev.candycanecazoo.hellstorm.game.GamePathing.walks(mob)) {
            return java.util.Optional.empty();
        }
        var graph = pathing.graph(mob);
        var from = dev.candycanecazoo.hellstorm.game.FlowNavigator.snap(graph, mob.blockPosition());
        var goal = dev.candycanecazoo.hellstorm.game.FlowNavigator.snap(graph, mob.getTarget().blockPosition());
        return from.flatMap(f -> goal.flatMap(g -> pathing.field(mob, mob.getTarget(), g)).map(field -> {
            List<BlockPos> trail = new ArrayList<>();
            trail.add(f);
            trail.addAll(field.descent(f, MAX_NODES, 0));
            return trail;
        }));
    }

    private static List<BlockPos> doorways(ServerLevel level) {
        List<Portal> portals = GameManager.get(level)
                .map(active -> active.pathing().doorways(ZOMBIE))
                .orElseGet(() -> {
                    ZombiesMap map = MapSavedData.get(level).map();
                    CachedPortals cached = NO_GAME_PORTALS.get(level.dimension());
                    if (cached == null || !cached.map().equals(map)) {
                        cached = new CachedPortals(map, portals(level, map));
                        NO_GAME_PORTALS.put(level.dimension(), cached);
                    }
                    return cached.portals();
                });
        List<BlockPos> cells = new ArrayList<>();
        for (Portal portal : portals) {
            cells.add(portal.cellA());
            if (!portal.cellB().equals(portal.cellA())) {
                cells.add(portal.cellB());
            }
        }
        return cells;
    }
}
