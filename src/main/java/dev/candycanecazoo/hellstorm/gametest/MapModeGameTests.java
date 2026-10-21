package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.SpawnMarkerBlock;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.MapReset;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Spawn markers hide while a game runs and show otherwise; edit mode refuses to start a game. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapModeGameTests {
    private MapModeGameTests() {}

    private static boolean hidden(GameTestHelper helper, BlockPos pos) {
        var state = helper.getLevel().getBlockState(pos);
        return state.getValue(SpawnMarkerBlock.HIDDEN) && state.getRenderShape() == RenderShape.INVISIBLE;
    }

    /** Switching mode also shows markers a crash mid-game, or a version that hid them for all of play mode, left hidden. */
    @GameTest(template = "floor", batch = "map_mode")
    public static void markersShowInBothModesAndEditModeBlocksGames(GameTestHelper helper) {
        var level = helper.getLevel();
        ZombiesMap map = MapSavedData.get(level).map();
        MapMode mode = MapSavedData.get(level).mode();
        BlockPos first = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        try {
            level.setBlockAndUpdate(first, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            helper.assertFalse(hidden(helper, first), "a marker placed outside a game should show");
            level.setBlock(first, level.getBlockState(first).setValue(SpawnMarkerBlock.HIDDEN, true), Block.UPDATE_CLIENTS);
            MapModes.set(level, MapMode.PLAY);
            helper.assertFalse(hidden(helper, first), "play mode with no game running should show the marker");

            MapModes.set(level, MapMode.EDIT);
            helper.assertFalse(hidden(helper, first), "edit mode should show the marker");
            helper.assertTrue(GameManager.create(level) instanceof GameManager.CreateResult.EditMode, "edit mode should refuse a game");
        } finally {
            MapModes.set(level, mode);
            MapEdits.apply(level, m -> map);
        }
        helper.succeed();
    }

    /**
     * A game hides its map's markers as it registers, and one placed mid-game hides itself. Ending the game shows them all
     * again, and further resets leave them showing.
     */
    @GameTest(template = "floor", batch = "map_markers_game")
    public static void markersHideWhileAGameRunsAndShowWhenItEnds(GameTestHelper helper) {
        var level = helper.getLevel();
        ZombiesMap saved = MapSavedData.get(level).map();
        BlockPos first = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        BlockPos second = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        BlockPos midGame = helper.absolutePos(new BlockPos(6, ROOM_Y, 2));
        GameManager.Active active = null;
        try {
            level.setBlockAndUpdate(first, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            level.setBlockAndUpdate(second, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            helper.assertFalse(hidden(helper, first) || hidden(helper, second), "markers should show before a game");
            BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
            ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_markers"), List.of(room), true))
                    .withFeature(first, FeatureKind.SPAWN).withFeature(second, FeatureKind.SPAWN);
            active = GameManager.register(level, GameMap.of(map, Set.of(), 6.0), List.of(helper.absolutePos(new BlockPos(4, ROOM_Y, 4))), id -> null);
            helper.assertTrue(hidden(helper, first) && hidden(helper, second), "a game should hide its markers");
            level.setBlockAndUpdate(midGame, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            helper.assertTrue(hidden(helper, midGame), "a marker placed during a game should hide itself");

            GameManager.end(active);
            active = null;
            helper.assertFalse(hidden(helper, first) || hidden(helper, second) || hidden(helper, midGame), "ending the game should show every marker");
            MapReset.reset(level, map);
            helper.assertFalse(hidden(helper, first) || hidden(helper, second), "a second reset should leave them showing");
        } finally {
            if (active != null) {
                GameManager.end(active);
            }
            MapEdits.apply(level, m -> saved);
        }
        helper.succeed();
    }
}
