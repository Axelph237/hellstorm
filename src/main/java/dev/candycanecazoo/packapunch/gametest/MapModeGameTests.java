package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.SpawnMarkerBlock;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.map.MapMode;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tools.MapEdits;
import dev.candycanecazoo.packapunch.tools.MapModes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Play mode hides the spawn markers and allows games; edit mode shows them and refuses to start one. */
@GameTestHolder(PackAPunch.MOD_ID)
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
