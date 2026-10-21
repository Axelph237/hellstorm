package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.SpawnMarkerBlock;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Play mode hides the spawn markers and allows games; edit mode shows them and refuses to start one. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapModeGameTests {
    private MapModeGameTests() {}

    private static boolean hidden(GameTestHelper helper, BlockPos pos) {
        var state = helper.getLevel().getBlockState(pos);
        return state.getValue(SpawnMarkerBlock.HIDDEN) && state.getRenderShape() == RenderShape.INVISIBLE;
    }

    @GameTest(template = "floor", batch = "map_mode")
    public static void playHidesMarkersAndEditShowsThemAndBlocksGames(GameTestHelper helper) {
        var level = helper.getLevel();
        ZombiesMap map = MapSavedData.get(level).map();
        MapMode mode = MapSavedData.get(level).mode();
        BlockPos first = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        BlockPos second = helper.absolutePos(new BlockPos(5, ROOM_Y, 5));
        try {
            level.setBlockAndUpdate(first, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            MapModes.set(level, MapMode.PLAY);
            helper.assertTrue(hidden(helper, first), "play mode should hide the marker");
            level.setBlockAndUpdate(second, PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            helper.assertTrue(hidden(helper, second), "a marker placed in play mode should hide itself");

            MapModes.set(level, MapMode.EDIT);
            helper.assertFalse(hidden(helper, first) || hidden(helper, second), "edit mode should show every marker");
            helper.assertTrue(GameManager.create(level) instanceof GameManager.CreateResult.EditMode, "edit mode should refuse a game");
        } finally {
            MapModes.set(level, mode);
            MapEdits.apply(level, m -> map);
        }
        helper.succeed();
    }
}
