package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Reads and changes a window's boards. Every barricade block in an opening always shows the same count. */
public final class WindowOps {
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
     * A board goes back on: the thud of landing on planks, from the middle of the opening. The repairer's cha-ching is
     * {@link Game}'s to play, since it plays only while the round's repair cap still pays.
     */
    public static void addBoard(ServerLevel level, WindowDef window) {
        int boards = boards(level, window);
        if (boards < window.maxBoards()) {
            setBoards(level, window, boards + 1);
            level.playSound(null, window.opening().center(), SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    /**
     * A player in a running game is holding use on a window with boards missing. Vanilla repeats block use while the
     * button is held, and so does the Hellstorm use key; the game puts a board back each {@code windows.repair_ticks} of
     * holding (see {@link Game#holdRepair}). Returns false if there's nothing to rebuild.
     */
    public static boolean tryRepair(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<GameManager.Active> active = GameManager.forPlayer(player.getUUID()).filter(a -> a.level() == level);
        Optional<WindowDef> window = active.flatMap(a -> a.game().map().map().windowAt(pos));
        if (window.isEmpty() || !active.get().game().targetable(player.getUUID()) || boards(level, window.get()) >= window.get().maxBoards()) {
            return false;
        }
        active.get().game().holdRepair(player.getUUID(), window.get().id());
        return true;
    }
}
