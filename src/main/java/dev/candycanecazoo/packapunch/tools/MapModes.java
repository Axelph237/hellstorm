package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.feature.SpawnMarkerBlock;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.MapMode;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Switching a level's map between edit and play: the mode is saved, and every spawn marker shows or hides to match. */
public final class MapModes {
    private MapModes() {}

    public static void set(ServerLevel level, MapMode mode) {
        MapSavedData.get(level).setMode(mode);
        for (BlockPos pos : MapSavedData.get(level).map().features(FeatureKind.SPAWN)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SpawnMarkerBlock && state.getValue(SpawnMarkerBlock.HIDDEN) != (mode == MapMode.PLAY)) {
                level.setBlock(pos, state.setValue(SpawnMarkerBlock.HIDDEN, mode == MapMode.PLAY), Block.UPDATE_CLIENTS);
            }
        }
    }
}
