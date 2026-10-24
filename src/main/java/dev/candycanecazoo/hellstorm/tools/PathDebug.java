package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.net.DebugPathsPacket;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * The {@code show_paths} debug view: zombies' current paths and where rooms connect, sent to players holding a map tool.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PathDebug {
    private static final int SEND_INTERVAL = 10;
    private static final int MAX_NODES = 64;

    private record CachedConnections(ZombiesMap map, List<BlockPos> cells) {}

    private static final Map<ResourceKey<Level>, CachedConnections> CONNECTIONS = new HashMap<>();

    private PathDebug() {}

    /** Where rooms connect on this level's map, with every door players buy still shut, read from the world now. */
    public static Passages.Result passages(ServerLevel level, ZombiesMap map) {
        return Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(level), ServerConfig.MAX_PASSAGE_GAP.get());
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
                if (level.getEntity(id) instanceof Mob mob && mob.getNavigation().getPath() != null) {
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

    /** One cell per connection between rooms: where a walker first crosses into the other room. */
    private static List<BlockPos> doorways(ServerLevel level) {
        ZombiesMap map = MapSavedData.get(level).map();
        CachedConnections cached = CONNECTIONS.get(level.dimension());
        if (cached == null || !cached.map().equals(map)) {
            cached = new CachedConnections(map, List.copyOf(passages(level, map).openings().values()));
            CONNECTIONS.put(level.dimension(), cached);
        }
        return cached.cells();
    }
}
