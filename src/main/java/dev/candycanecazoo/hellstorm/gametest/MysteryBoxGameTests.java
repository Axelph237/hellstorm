package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxes;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.game;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Rolling the Mystery Crate: paying, the roll, the offer only the roller can take, and moving. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MysteryBoxGameTests {
    private MysteryBoxGameTests() {}

    /** A crate with its foot at {@code x, z} and its head one block south. */
    private static BlockPos box(GameTestHelper helper, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, ROOM_Y, z));
        MysteryBoxBlock.place(helper.getLevel(), at, Direction.SOUTH);
        return at;
    }

    private static MysteryBoxBlock.Entity entity(GameTestHelper helper, BlockPos at) {
        return (MysteryBoxBlock.Entity) helper.getLevel().getBlockEntity(at);
    }

    private static MysteryBoxBlock.Presence presence(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getBlockState(at).getValue(MysteryBoxBlock.BOX);
    }

    private static boolean active(GameTestHelper helper, BlockPos at) {
        return presence(helper, at) == MysteryBoxBlock.Presence.HERE;
    }

    /** Whether both halves have something to bump into. */
    private static boolean solid(GameTestHelper helper, BlockPos foot) {
        return !helper.getLevel().getBlockState(foot).getCollisionShape(helper.getLevel(), foot).isEmpty()
                && !helper.getLevel().getBlockState(foot.south()).getCollisionShape(helper.getLevel(), foot.south()).isEmpty();
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

    /** Rolled from the head, since either half of the crate sells. */
    @GameTest(template = "floor", batch = "box_roll", timeoutTicks = 400)
    public static void aRollChargesThenOffersTheResultOnlyToTheRollerAndTheLidCloses(GameTestHelper helper) {
        BlockPos at = box(helper, 2, 2);
        BlockPos head = at.south();
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

            helper.assertTrue(helper.getLevel().getBlockState(head).is(PapRegistries.MYSTERY_BOX.get())
                    && helper.getLevel().getBlockEntity(head) == null, "the crate is two blocks, and only the foot holds it");
            active.game().refund(roller.getUUID(), cost);
            FeatureBlock.use(roller, head);
            helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.ROLLING, "the box rolls from its head");
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
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.CLOSING && box.result().isEmpty(), "taking the result empties the box and closes the lid");
                helper.assertTrue(has(roller, offered), "the roller has the offered item");
                assertPoints(helper, active, roller, start, "taking the result is free");
                active.game().refund(roller.getUUID(), cost);
                int before = points(active, roller);
                FeatureBlock.use(roller, at);
                assertPoints(helper, active, roller, before, "a closing box charges nothing");
            } catch (RuntimeException | AssertionError e) {
                GameManager.end(active);
                throw e;
            }
        });
        helper.runAfterDelay(rollTicks + 2 + MysteryBoxBlock.LID_TICKS + 2, () -> {
            try {
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.IDLE, "the lid shuts after LID_TICKS, is " + box.phase());
            } finally {
                GameManager.end(active);
            }
            helper.assertTrue(presence(helper, at) == MysteryBoxBlock.Presence.STORED && solid(helper, at), "ending the game puts the box away");
            helper.succeed();
        });
    }

    /** An offer nobody takes goes up in smoke when it runs out, and then the lid closes. */
    @GameTest(template = "floor", batch = "box_expire", timeoutTicks = 600)
    public static void anOfferNobodyTakesGoesAndTheLidCloses(GameTestHelper helper) {
        BlockPos at = box(helper, 2, 2);
        ServerPlayer roller = player(helper, "pap_box_leaver");
        GameManager.Active active = game(helper, roller, at);
        int ticks = active.game().settings().box().rollTicks() + active.game().settings().box().collectTicks();
        MysteryBoxBlock.Entity box = entity(helper, at);
        active.game().refund(roller.getUUID(), active.game().settings().box().cost());
        FeatureBlock.use(roller, at);
        helper.runAfterDelay(ticks + 2, () -> {
            try {
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.CLOSING && box.result().isEmpty(), "the offer runs out and the lid closes, is " + box.phase());
            } catch (RuntimeException | AssertionError e) {
                GameManager.end(active);
                throw e;
            }
        });
        helper.runAfterDelay(ticks + 2 + MysteryBoxBlock.LID_TICKS + 2, () -> {
            try {
                helper.assertTrue(box.phase() == MysteryBoxBlock.Phase.IDLE && active(helper, at), "then the box waits for the next roll, is " + box.phase());
            } finally {
                GameManager.end(active);
            }
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
            helper.assertTrue(presence(helper, home) == MysteryBoxBlock.Presence.AWAY && !solid(helper, home)
                    && presence(helper, home.south()) == MysteryBoxBlock.Presence.AWAY, "the crate it left is gone, both halves, with nothing to bump into");
            helper.assertTrue(solid(helper, to), "the crate it went to is there");
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
        helper.assertTrue(locations.stream().allMatch(pos -> presence(helper, pos) == MysteryBoxBlock.Presence.STORED && solid(helper, pos)),
                "ending the game puts every box back for map makers");
        helper.succeed();
    }
}
