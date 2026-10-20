package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.feature.BarricadeBlock;
import dev.candycanecazoo.packapunch.map.WindowDef;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reads and changes a window's boards. Every barricade block in an opening always shows the same count. */
public final class WindowOps {
    private static final Map<UUID, Long> NEXT_REPAIR = new ConcurrentHashMap<>();

    private WindowOps() {}

    public static int boards(ServerLevel level, WindowDef window) {
        BlockState state = level.getBlockState(window.opening().min());
        return state.is(PapRegistries.BARRICADE.get()) ? state.getValue(BarricadeBlock.BOARDS) : 0;
    }

    /** Fills the opening with barricade blocks showing {@code boards}. Also used to create and reset windows. */
    public static void setBoards(ServerLevel level, WindowDef window, int boards) {
        BlockState state = PapRegistries.BARRICADE.get().defaultBlockState()
                .setValue(BarricadeBlock.BOARDS, Math.max(0, Math.min(window.maxBoards(), boards)))
                .setValue(BarricadeBlock.FACING, window.outward());
        for (BlockPos pos : BlockPos.betweenClosed(window.opening().min(), window.opening().max())) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    public static void resetAll(ServerLevel level, ZombiesMap map) {
        map.windows().values().forEach(w -> setBoards(level, w, w.maxBoards()));
    }

    public static void tearBoard(ServerLevel level, WindowDef window) {
        int boards = boards(level, window);
        if (boards > 0) {
            setBoards(level, window, boards - 1);
            level.playSound(null, window.bottomCenter(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6f, 1.2f);
        }
    }

    /**
     * A player in a running game puts one board back, at most once per {@code windows.repair_ticks}. Holding use repeats
     * this, since vanilla repeats block use while the button is held. Returns false if nothing happened.
     */
    public static boolean tryRepair(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<GameManager.Active> active = GameManager.forPlayer(player.getUUID()).filter(a -> a.level() == level);
        Optional<WindowDef> window = active.flatMap(a -> a.game().map().map().windowAt(pos));
        if (window.isEmpty() || !active.get().game().targetable(player.getUUID())) {
            return false;
        }
        long now = level.getGameTime();
        if (NEXT_REPAIR.getOrDefault(player.getUUID(), 0L) > now) {
            return true;
        }
        int boards = boards(level, window.get());
        if (boards >= window.get().maxBoards()) {
            return false;
        }
        NEXT_REPAIR.put(player.getUUID(), now + active.get().game().settings().windows().repairTicks());
        setBoards(level, window.get(), boards + 1);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
        active.get().game().onRepair(player.getUUID());
        return true;
    }
}
