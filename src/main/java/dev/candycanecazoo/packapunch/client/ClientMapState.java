package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.SpawnLinks;
import net.minecraft.core.BlockPos;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.net.DebugPathsPacket;
import dev.candycanecazoo.packapunch.tools.Selection;

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
    private static java.util.Optional<String> selectedDoor = java.util.Optional.empty();

    private ClientMapState() {}

    public static void setMap(ZombiesMap next) {
        map = next;
        index = ZoneIndex.of(next);
        spawnLinks = null;
    }

    public static void setSelection(Selection next) {
        selection = next;
    }

    public static void setSelectedDoor(java.util.Optional<String> door) {
        selectedDoor = door;
    }

    public static java.util.Optional<String> selectedDoor() {
        return selectedDoor;
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
        selectedDoor = java.util.Optional.empty();
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
