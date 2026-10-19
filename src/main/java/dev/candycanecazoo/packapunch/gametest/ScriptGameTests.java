package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.game.Game;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameEvents;
import dev.candycanecazoo.packapunch.game.GameMap;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.script.ForgeScriptHost;
import dev.candycanecazoo.packapunch.script.LuaRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScriptGameTests {
    private static final LuaRuntime.Limits LIMITS = new LuaRuntime.Limits(100_000, 5, 10_000);

    private ScriptGameTests() {}

    private static Game emptyGame() {
        return new Game(GameSettings.DEFAULT, GameMap.of(ZombiesMap.EMPTY, Set.of()), RandomSource.create(), new GameEvents());
    }

    private static Map<String, String> setblockOnRoundStart(BlockPos target) {
        return Map.of("test:main", "events.on('round_start', function(e) command('setblock " + target.getX() + " " + target.getY() + " "
                + target.getZ() + " minecraft:gold_block') end)");
    }

    @GameTest(template = "floor")
    public static void scriptCommandsChangeTheWorld(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        Game game = emptyGame();
        LuaRuntime runtime = LuaRuntime.build(setblockOnRoundStart(target), List.of(),
                new ForgeScriptHost(helper.getLevel(), game, target, 2, true), LIMITS);
        helper.assertTrue(runtime.loadErrors().isEmpty(), "load errors: " + runtime.loadErrors());
        runtime.dispatch(new GameEvent.RoundStart(1));
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(4, ROOM_Y, 4));
        helper.succeed();
    }

    @GameTest(template = "floor")
    public static void scriptCommandsRespectThePermissionLevel(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        LuaRuntime runtime = LuaRuntime.build(setblockOnRoundStart(target), List.of(),
                new ForgeScriptHost(helper.getLevel(), emptyGame(), target, 0, true), LIMITS);
        runtime.dispatch(new GameEvent.RoundStart(1));
        helper.assertBlockNotPresent(Blocks.GOLD_BLOCK, new BlockPos(4, ROOM_Y, 4));
        helper.succeed();
    }

    @GameTest(template = "floor")
    public static void checkModeNeverTouchesTheWorld(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        Map<String, String> scripts = Map.of("test:main", "command('setblock " + target.getX() + " " + target.getY() + " " + target.getZ()
                + " minecraft:gold_block')");
        LuaRuntime.build(scripts, List.of(), new ForgeScriptHost(helper.getLevel(), emptyGame(), target, 4, false), LIMITS);
        helper.assertBlockNotPresent(Blocks.GOLD_BLOCK, new BlockPos(4, ROOM_Y, 4));
        helper.succeed();
    }
}
