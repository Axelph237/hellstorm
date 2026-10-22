package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Each level's undo and redo of map edits, shared by everyone editing it. Kept in memory only, so a restart clears it.
 * Undoing puts back the rooms, doors, windows, and features the edit changed, and the blocks it changed, and leaves the
 * rest of the map as it is now.
 */
public final class MapHistory {
    static final int LIMIT = 64;

    /** One edit: what it did, the map before and after, and the blocks it changed before and after. */
    public record Change(String what, ZombiesMap before, ZombiesMap after, Map<BlockPos, BlockState> blocksBefore,
                         Map<BlockPos, BlockState> blocksAfter) {}

    private record Stacks(Deque<Change> undo, Deque<Change> redo) {}

    private static final Map<ResourceKey<Level>, Stacks> LEVELS = new ConcurrentHashMap<>();

    private MapHistory() {}

    public static void edit(ServerLevel level, String what, UnaryOperator<ZombiesMap> edit) {
        record(level, what, List.of(), () -> MapEdits.apply(level, edit));
    }

    /** Runs {@code change}, which may edit the map and the blocks at {@code blocks}, and records it if anything changed. */
    public static void record(ServerLevel level, String what, Iterable<BlockPos> blocks, Runnable change) {
        ZombiesMap before = MapSavedData.get(level).map();
        Map<BlockPos, BlockState> blocksBefore = states(level, blocks);
        change.run();
        ZombiesMap after = MapSavedData.get(level).map();
        Map<BlockPos, BlockState> blocksAfter = states(level, blocks);
        if (before.equals(after) && blocksBefore.equals(blocksAfter)) {
            return;
        }
        Stacks stacks = LEVELS.computeIfAbsent(level.dimension(), k -> new Stacks(new ArrayDeque<>(), new ArrayDeque<>()));
        stacks.undo().push(new Change(what, before, after, blocksBefore, blocksAfter));
        while (stacks.undo().size() > LIMIT) {
            stacks.undo().removeLast();
        }
        stacks.redo().clear();
    }

    /** Undoes the level's last edit and returns what it was, or nothing when there is none. */
    public static Optional<String> undo(ServerLevel level) {
        return step(level, true);
    }

    public static Optional<String> redo(ServerLevel level) {
        return step(level, false);
    }

    private static Optional<String> step(ServerLevel level, boolean undo) {
        Stacks stacks = LEVELS.get(level.dimension());
        Deque<Change> from = stacks == null ? null : undo ? stacks.undo() : stacks.redo();
        if (from == null || from.isEmpty()) {
            return Optional.empty();
        }
        Change change = from.pop();
        (undo ? stacks.redo() : stacks.undo()).push(change);
        ZombiesMap source = undo ? change.after() : change.before();
        ZombiesMap target = undo ? change.before() : change.after();
        MapEdits.apply(level, map -> map.patched(source, target));
        (undo ? change.blocksBefore() : change.blocksAfter()).forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
        return Optional.of(change.what());
    }

    public static void clear() {
        LEVELS.clear();
    }

    private static Map<BlockPos, BlockState> states(ServerLevel level, Iterable<BlockPos> blocks) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        blocks.forEach(pos -> states.put(pos.immutable(), level.getBlockState(pos)));
        return states;
    }
}
