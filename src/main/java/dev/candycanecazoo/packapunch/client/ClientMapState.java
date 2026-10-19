package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.tools.Selection;

import java.util.List;

/** The client's mirror of what the server told it about the current level's map and this player's tools. */
public final class ClientMapState {
    private static ZombiesMap map = ZombiesMap.EMPTY;
    private static ZoneIndex index = ZoneIndex.of(ZombiesMap.EMPTY);
    private static Selection selection = Selection.NONE;
    private static List<BlockBox> preview = List.of();
    private static List<Problem> problems = List.of();

    private ClientMapState() {}

    public static void setMap(ZombiesMap next) {
        map = next;
        index = ZoneIndex.of(next);
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

    public static void reset() {
        setMap(ZombiesMap.EMPTY);
        selection = Selection.NONE;
        preview = List.of();
        problems = List.of();
    }

    public static ZombiesMap map() {
        return map;
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
