package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.feature.SpawnMarkerBlock;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import net.minecraft.server.level.ServerLevel;

/**
 * Switching a level's map between edit and play. The mode only decides whether games can start; the spawn markers follow
 * whether a game is running, and are put right here too, which fixes markers a crash mid-game or an older version (that hid
 * them for all of play mode) left hidden.
 */
public final class MapModes {
    private MapModes() {}

    public static void set(ServerLevel level, MapMode mode) {
        MapSavedData.get(level).setMode(mode);
        SpawnMarkerBlock.setHidden(level, MapSavedData.get(level).map().features(FeatureKind.SPAWN), GameManager.get(level).isPresent());
    }
}
