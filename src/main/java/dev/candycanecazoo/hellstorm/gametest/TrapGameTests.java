package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import dev.candycanecazoo.hellstorm.feature.TrapCycle;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.MapReset;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.game;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Traps: buying gives a redstone signal for its active time, then a cooldown; strength and reset. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrapGameTests {
    private TrapGameTests() {}

    private static TrapControllerBlock.Entity trap(GameTestHelper helper, BlockPos at, TrapControllerBlock.Config config) {
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.TRAP_CONTROLLER.get().defaultBlockState().setValue(TrapControllerBlock.FACING, Direction.NORTH));
        TrapControllerBlock.Entity trap = (TrapControllerBlock.Entity) helper.getLevel().getBlockEntity(at);
        trap.setConfig(config);
        return trap;
    }

    private static TrapControllerBlock.Config config(int cost, boolean power, int active, int cooldown, int signal) {
        return new TrapControllerBlock.Config(Optional.empty(), Optional.of(cost), power, Optional.of(active), Optional.of(cooldown), signal);
    }

    private static boolean powered(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getBlockState(at).getValue(TrapControllerBlock.POWERED);
    }

    private static boolean lit(GameTestHelper helper, BlockPos lamp) {
        return helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
    }

    private static void dust(GameTestHelper helper, BlockPos from, Direction along, int count) {
        for (int i = 0; i < count; i++) {
            helper.getLevel().setBlockAndUpdate(from.relative(along, i), Blocks.REDSTONE_WIRE.defaultBlockState());
        }
    }

    /** Ends the game before failing, so a broken assertion doesn't leave it running for later tests. */
    private static void guarded(GameManager.Active active, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            GameManager.end(active);
            throw e;
        }
    }

    @GameTest(template = "floor", timeoutTicks = 100, batch = "trap_signal")
    public static void aBoughtTrapPowersANeighbourAndADustLineForExactlyItsActiveTicks(GameTestHelper helper) {
        int activeTicks = 30;
        BlockPos at = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        BlockPos neighbour = at.south();
        BlockPos lineEnd = at.west(4);
        TrapControllerBlock.Entity trap = trap(helper, at, config(100, false, activeTicks, 40, 15));
        helper.getLevel().setBlockAndUpdate(neighbour, Blocks.REDSTONE_LAMP.defaultBlockState());
        dust(helper, at.west(), Direction.WEST, 3);
        helper.getLevel().setBlockAndUpdate(lineEnd, Blocks.REDSTONE_LAMP.defaultBlockState());
        ServerPlayer player = player(helper, "pap_trap_signal");
        GameManager.Active active = game(helper, player, at);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        guarded(active, () -> {
            active.game().refund(player.getUUID(), 1000);
            int before = points(active, player);
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == before - 100, "expected to pay 100, have " + points(active, player));
            helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.ACTIVE, "the trap should be running, is " + trap.cycle());
            helper.assertTrue(events.contains(new GameEvent.TrapActivated(player.getUUID(), at)), "no trap_activated event: " + events);
            helper.assertTrue(lit(helper, neighbour) && lit(helper, lineEnd), "both lamps light at once");
        });
        int[] poweredTicks = {0};
        helper.onEachTick(() -> guarded(active, () -> {
            if (powered(helper, at)) {
                poweredTicks[0]++;
                helper.assertTrue(lit(helper, neighbour) && lit(helper, lineEnd), "a lamp went out while the trap was on");
            }
        }));
        // A lamp turns off 4 ticks after losing power.
        helper.runAtTickTime(activeTicks + 10, () -> guarded(active, () -> {
            helper.assertTrue(poweredTicks[0] == activeTicks, "powered for " + poweredTicks[0] + " ticks, expected " + activeTicks);
            helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.COOLDOWN, "after its active time it cools down, is " + trap.cycle());
            helper.assertFalse(lit(helper, neighbour) || lit(helper, lineEnd), "the lamps should be off");
            GameManager.end(active);
            helper.succeed();
        }));
    }

    @GameTest(template = "floor", timeoutTicks = 100, batch = "trap_refuse")
    public static void aTrapRefusesWhileRunningAndCoolingDownThenSellsAgain(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        TrapControllerBlock.Entity trap = trap(helper, at, config(100, false, 5, 10, 15));
        ServerPlayer player = player(helper, "pap_trap_refuse");
        GameManager.Active active = game(helper, player, at);
        int[] start = new int[1];
        guarded(active, () -> {
            active.game().refund(player.getUUID(), 1000);
            start[0] = points(active, player);
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start[0] - 100, "the first purchase should cost 100");
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start[0] - 100, "a running trap was sold again");
            helper.assertTrue(trap.prompt().isEmpty(), "a running trap shouldn't prompt");
        });
        helper.runAtTickTime(8, () -> guarded(active, () -> {
            helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.COOLDOWN, "should be cooling down, is " + trap.cycle());
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start[0] - 100, "a cooling trap was sold");
        }));
        helper.runAtTickTime(20, () -> guarded(active, () -> {
            helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.READY, "should be ready again, is " + trap.cycle());
            helper.assertTrue(trap.prompt().isPresent(), "a ready trap prompts");
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start[0] - 200, "a ready trap should sell again, have " + points(active, player));
            helper.assertTrue(powered(helper, at), "bought again, it should be on");
            GameManager.end(active);
            helper.assertFalse(powered(helper, at), "ending the game turns the trap off");
            helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.READY, "ending the game makes it ready");
            helper.succeed();
        }));
    }

    @GameTest(template = "floor", batch = "trap_name")
    public static void aNamedTrapPromptsWithItsName(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        TrapControllerBlock.Entity trap = trap(helper, at, config(100, false, 5, 10, 15));
        helper.assertTrue(trap.prompt().orElseThrow().label().equals("Trap"), "an unnamed trap is a Trap");
        TrapControllerBlock.Config named = trap.config().withName(Optional.of("Flogger"));
        net.minecraft.nbt.Tag saved = TrapControllerBlock.Config.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, named).getOrThrow(false, e -> {});
        helper.assertTrue(trap.trySet(saved).isEmpty(), "the settings screen's save should take the name");
        helper.assertTrue(trap.prompt().orElseThrow().label().equals("Flogger"), "got " + trap.prompt());
        helper.assertTrue(trap.trySet(trap.config().withName(Optional.of("x".repeat(TrapControllerBlock.Config.MAX_NAME + 1)))).isPresent(),
                "a name that long should be refused");
        helper.assertTrue(trap.prompt().orElseThrow().label().equals("Flogger"), "a refused name mustn't stick");
        helper.assertTrue(trap.trySet(trap.config().withName(Optional.empty())).isEmpty(), "clearing the name should be allowed");
        helper.assertTrue(trap.prompt().orElseThrow().label().equals("Trap"), "clearing the name goes back to Trap");
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "trap_power")
    public static void aTrapThatNeedsPowerWaitsForThePower(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        BlockPos lever = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(lever, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        TrapControllerBlock.Entity trap = trap(helper, at, config(100, true, 40, 40, 15));
        ServerPlayer player = player(helper, "pap_trap_power");
        GameManager.Active active = game(helper, player, at, lever);
        try {
            active.game().refund(player.getUUID(), 1000);
            int start = points(active, player);
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start && trap.cycle().phase() == TrapCycle.Phase.READY,
                    "bought without power: " + trap.cycle() + ", " + points(active, player) + " points");
            active.game().turnPowerOn(Optional.empty());
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start - 100 && powered(helper, at), "with the power on it should sell");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "trap_strength")
    public static void signalFiveReachesFiveDustAndNoFurther(GameTestHelper helper) {
        BlockPos near = helper.absolutePos(new BlockPos(8, ROOM_Y, 2));
        BlockPos far = helper.absolutePos(new BlockPos(8, ROOM_Y, 6));
        TrapControllerBlock.Entity five = trap(helper, near, config(0, false, 200, 0, 5));
        TrapControllerBlock.Entity six = trap(helper, far, config(0, false, 200, 0, 5));
        dust(helper, near.west(), Direction.WEST, 5);
        BlockPos litLamp = near.west(6);
        helper.getLevel().setBlockAndUpdate(litLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        dust(helper, far.west(), Direction.WEST, 6);
        BlockPos darkLamp = far.west(7);
        helper.getLevel().setBlockAndUpdate(darkLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        five.activate(helper.getLevel());
        six.activate(helper.getLevel());
        helper.runAfterDelay(2, () -> {
            int first = helper.getLevel().getBlockState(near.west()).getValue(RedStoneWireBlock.POWER);
            helper.assertTrue(first == 5, "the dust next to the trap carries " + first + ", expected 5");
            helper.assertTrue(lit(helper, litLamp), "the lamp after 5 dust should light");
            helper.assertFalse(lit(helper, darkLamp), "the lamp after 6 dust should stay dark");
            helper.succeed();
        });
    }

    @GameTest(template = "floor", batch = "trap_reset")
    public static void resetLeavesATrapReadyAndOff(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        BlockPos lamp = at.east();
        helper.getLevel().setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        TrapControllerBlock.Entity trap = trap(helper, at, config(0, false, 200, 200, 15));
        trap.activate(helper.getLevel());
        helper.assertTrue(powered(helper, at) && lit(helper, lamp), "activating powers the lamp");
        ZombiesMap map = ZombiesMap.EMPTY.withFeature(at, FeatureKind.TRAP);
        MapReset.reset(helper.getLevel(), map);
        MapReset.reset(helper.getLevel(), map);
        helper.assertFalse(powered(helper, at), "reset should turn the trap off");
        helper.assertTrue(trap.cycle().phase() == TrapCycle.Phase.READY, "reset should make it ready, is " + trap.cycle());
        helper.runAfterDelay(6, () -> {
            helper.assertFalse(lit(helper, lamp), "the lamp should go out after a reset");
            helper.succeed();
        });
    }
}
