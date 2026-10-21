package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.SpawnLinks;
import net.minecraft.core.BlockPos;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.net.DebugPathsPacket;
import dev.candycanecazoo.hellstorm.tools.Selection;

import java.util.List;
import java.util.Map;

/** The client's mirror of what the server told it about the current level's map and this player's tools. */
public final class ClientMapState {
    private static ZombiesMap map = ZombiesMap.EMPTY;
    private static ZoneIndex index = ZoneIndex.of(ZombiesMap.EMPTY);
    private static Map<BlockPos, SpawnLinks.Link> spawnLinks;
    private static Selection selection = Selection.NONE;
    private static List<BlockBox> preview = List.of();
    private static List<Problem> problems = List.of();
    private static DebugPathsPacket debugPaths;
    private static long debugPathsAt;

    private ClientMapState() {}

    public static void setMap(ZombiesMap next) {
        map = next;
        index = ZoneIndex.of(next);
        spawnLinks = null;
    }

    public static void setSelection(Selection next) {
        selection = next;
    }

    public static void setPreview(List<BlockBox> next) {
        preview = List.copyOf(next);
    }

    public static void setProblems(List<Problem> next) {
        problems = List.copyOf(next);
    }

    public static void setDebugPaths(DebugPathsPacket paths) {
        debugPaths = paths;
        debugPathsAt = net.minecraft.Util.getMillis();
    }

    /** The last {@code show_paths} update, if it arrived recently. The server stops sending when the rule is off. */
    public static java.util.Optional<DebugPathsPacket> debugPaths() {
        return debugPaths != null && net.minecraft.Util.getMillis() - debugPathsAt < 1500 ? java.util.Optional.of(debugPaths) : java.util.Optional.empty();
    }

    public static void reset() {
        debugPaths = null;
        setMap(ZombiesMap.EMPTY);
        selection = Selection.NONE;
        preview = List.of();
        problems = List.of();
    }

    public static ZombiesMap map() {
        return map;
    }

    /**
     * Resolved on first use, with the server config's link range (Forge syncs it to clients). Not in {@link #setMap}:
     * maps arrive and reset while a world is still loading, before the config is.
     */
    public static Map<BlockPos, SpawnLinks.Link> spawnLinks() {
        if (spawnLinks == null) {
            spawnLinks = SpawnLinks.resolve(map, ServerConfig.GAME.get().windows().linkRange());
        }
        return spawnLinks;
    }

    public static ZoneIndex index() {
        return index;
    }

    public static Selection selection() {
        return selection;
    }

    public static List<BlockBox> preview() {
        return preview;
    }

    public static List<Problem> problems() {
        return problems;
    }
}
