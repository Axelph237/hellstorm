package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.MysteryBoxBlock;
import dev.candycanecazoo.packapunch.feature.MysteryBoxes;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.game;
import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Rolling the Mystery Box: paying, the roll, the offer only the roller can take, and moving. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MysteryBoxGameTests {
    private MysteryBoxGameTests() {}

    private static BlockPos box(GameTestHelper helper, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, ROOM_Y, z));
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.MYSTERY_BOX.get().defaultBlockState());
        return at;
    }

    private static MysteryBoxBlock.Entity entity(GameTestHelper helper, BlockPos at) {
        return (MysteryBoxBlock.Entity) helper.getLevel().getBlockEntity(at);
    }

    private static boolean active(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getBlockState(at).getValue(MysteryBoxBlock.ACTIVE);
    }

    private static int items(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void assertPoints(GameTestHelper helper, GameManager.Active active, ServerPlayer player, int expected, String why) {
        int actual = points(active, player);
        helper.assertTrue(actual == expected, why + ": expected " + expected + " points, have " + actual);
    }

    @GameTest(template = "floor", batch = "box_roll", timeoutTicks = 400)
    public static void aRollChargesThenOffersTheResultOnlyToTheRoller(GameTestHelper helper) {
        BlockPos at = box(helper, 2, 2);
        ServerPlayer roller = player(helper, "pap_box_roller");
        ServerPlayer other = player(helper, "pap_box_other");
        GameManager.Active active = game(helper, roller, at);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        int cost = active.game().settings().box().cost();
        int rollTicks = active.game().settings().box().rollTicks();
        MysteryBoxBlock.Entity box = entity(helper, at);
        int start = points(active, roller);
        try {
            active.game().join(other.getUUID());
            helper.assertTrue(active(helper, at), "the only location gets the box when the game starts");
            helper.assertTrue(box.price() == cost, "the box shows its price, " + box.price());
            helper.assertTrue(start < cost, "the test expects starting points below the box cost");
            FeatureBlock.use(roller, at);
            helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.IDLE, "rolled without enough points");
            assertPoints(helper, active, roller, start, "a refused roll charges nothing");

            active.game().refund(roller.getUUID(), cost);
            FeatureBlock.use(roller, at);
            helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.ROLLING, "the box rolls");
            assertPoints(helper, active, roller, start, "a roll costs box.cost");
            FeatureBlock.use(other, at);
            assertPoints(helper, active, other, start, "a busy box charges nothing");
        } catch (RuntimeException e) {
            GameManager.end(active);
            throw e;
        }
        helper.runAfterDelay(rollTicks / 2, () -> {
            if (box.phase() != MysteryBoxBlock.Phase.ROLLING) {
                GameManager.end(active);
                helper.fail("the result came before box.roll_ticks");
            }
        });
        helper.runAfterDelay(rollTicks + 2, () -> {
            try {
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.OFFERING, "the box offers a result after box.roll_ticks, is " + box.phase());
                helper.assertTrue(!box.result().isEmpty(), "the offer has an item");
                helper.assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.MysteryBoxOpened o && o.player().equals(roller.getUUID())),
                        "a mystery_box_opened event");
                int others = items(other);
                FeatureBlock.use(other, at);
                helper.assertTrue(items(other) == others && box.phase() == MysteryBoxBlock.Phase.OFFERING, "someone else can't take the result");
                ItemStack offered = box.result().copy();
                FeatureBlock.use(roller, at);
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.IDLE, "taking the result empties the box");
                helper.assertTrue(has(roller, offered), "the roller has the offered item");
                assertPoints(helper, active, roller, start, "taking the result is free");
            } finally {
                GameManager.end(active);
            }
            helper.assertFalse(active(helper, at), "ending the game puts the box away");
            helper.succeed();
        });
    }

    private static boolean has(ServerPlayer player, ItemStack offered) {
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, offered)) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "floor", batch = "box_move")
    public static void aMoveRefundsTheRollAndLeavesOneActiveLocation(GameTestHelper helper) {
        List<BlockPos> locations = List.of(box(helper, 2, 2), box(helper, 6, 2), box(helper, 2, 6));
        ServerPlayer roller = player(helper, "pap_box_mover");
        GameManager.Active active = game(helper, roller, locations.toArray(BlockPos[]::new));
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        try {
            helper.assertTrue(locations.stream().filter(pos -> active(helper, pos)).count() == 1, "exactly one location starts with the box");
            BlockPos home = MysteryBoxes.home(active).orElseThrow();
            helper.assertTrue(active(helper, home), "the box is at its home");
            active.game().refund(roller.getUUID(), active.game().settings().box().cost());
            int before = points(active, roller);
            FeatureBlock.use(roller, home);
            helper.assertTrue(points(active, roller) < before, "the roll was paid for");

            MysteryBoxes.finishRoll(active, entity(helper, home), true);
            assertPoints(helper, active, roller, before, "a move refunds the roll");
            helper.assertTrue(locations.stream().filter(pos -> active(helper, pos)).count() == 1, "exactly one location has the box after a move");
            BlockPos to = MysteryBoxes.home(active).orElseThrow();
            helper.assertTrue(!to.equals(home) && active(helper, to) && !active(helper, home), "the box moved somewhere else");
            helper.assertTrue(entity(helper, home).phase() == MysteryBoxBlock.Phase.IDLE, "the old location is idle");
            helper.assertTrue(events.contains(new GameEvent.MysteryBoxMoved(home, to)), "a mystery_box_moved event, got " + events);
            FeatureBlock.use(roller, home);
            assertPoints(helper, active, roller, before, "the old location can't be rolled");

            MysteryBoxes.setFireSale(active, true);
            helper.assertTrue(locations.stream().allMatch(pos -> active(helper, pos)), "a Fire Sale opens every location");
            helper.assertTrue(entity(helper, home).price() == active.game().settings().box().fireSaleCost(), "at the Fire Sale price");
            MysteryBoxes.setFireSale(active, false);
            helper.assertTrue(locations.stream().filter(pos -> active(helper, pos)).toList().equals(List.of(to)), "only the home stays open after it");
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(locations.stream().noneMatch(pos -> active(helper, pos)), "ending the game puts every box away");
        helper.succeed();
    }
}
