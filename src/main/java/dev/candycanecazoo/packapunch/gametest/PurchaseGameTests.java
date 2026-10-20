package dev.candycanecazoo.packapunch.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.PowerSwitchBlock;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.GameMap;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Buying from machines through the shared purchase path. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurchaseGameTests {
    private PurchaseGameTests() {}

    /** One start zone over the test floor, with {@code features} indexed, and a fake player joined to its game. */
    static GameManager.Active game(GameTestHelper helper, ServerPlayer player, BlockPos... features) {
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_shop"), List.of(room), true));
        for (BlockPos pos : features) {
            BlockState state = helper.getLevel().getBlockState(pos);
            map = map.withFeature(pos, ((FeatureBlock) state.getBlock()).featureKind());
        }
        BlockPos stand = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        return active;
    }

    static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static int points(GameManager.Active active, ServerPlayer player) {
        return active.game().player(player.getUUID()).orElseThrow().points();
    }

    @GameTest(template = "floor", batch = "shop_power")
    public static void thePowerSwitchTurnsThePowerOnForItsCostAndResetsWhenTheGameEnds(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        BlockPos second = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(second, helper.getLevel().getBlockState(at));
        ServerPlayer player = player(helper, "pap_power_test");
        GameManager.Active active = game(helper, player, at, second);
        try {
            helper.assertFalse(active.game().powerOn(), "a map with a power switch starts unpowered");
            int start = points(active, player);
            ((PowerSwitchBlock.Entity) helper.getLevel().getBlockEntity(at)).setConfig(new PowerSwitchBlock.Config(start + 1));
            FeatureBlock.use(player, at);
            helper.assertFalse(active.game().powerOn(), "turned on without enough points");
            helper.assertTrue(points(active, player) == start, "charged for a refused purchase");

            ((PowerSwitchBlock.Entity) helper.getLevel().getBlockEntity(at)).setConfig(new PowerSwitchBlock.Config(100));
            FeatureBlock.use(player, at);
            helper.assertTrue(active.game().powerOn(), "the power didn't come on");
            helper.assertTrue(points(active, player) == start - 100, "expected to pay 100, have " + points(active, player));
            for (BlockPos pos : List.of(at, second)) {
                helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "every switch flips on, not " + pos);
            }
            helper.assertTrue(helper.getLevel().getSignal(at.below(), Direction.DOWN) > 0, "a pulled switch powers redstone like a lever");
            FeatureBlock.use(player, at);
            helper.assertTrue(points(active, player) == start - 100, "the power can only be bought once");
        } finally {
            GameManager.end(active);
        }
        helper.assertFalse(helper.getLevel().getBlockState(at).getValue(LeverBlock.POWERED), "ending the game resets the switch");
        helper.succeed();
    }

    /** Fake players ignore damage anyway, so this asks the damage event itself, which is what god mode cancels. */
    @GameTest(template = "floor")
    public static void godModeCancelsDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_god_test");
        var source = helper.getLevel().damageSources().generic();
        helper.assertTrue(net.minecraftforge.common.ForgeHooks.onPlayerAttack(player, source, 10), "damage is cancelled without god mode");
        dev.candycanecazoo.packapunch.game.Cheats.set(player.getUUID(), dev.candycanecazoo.packapunch.game.Cheats.Cheat.GOD, true);
        try {
            helper.assertFalse(net.minecraftforge.common.ForgeHooks.onPlayerAttack(player, source, 10), "god mode let damage through");
        } finally {
            dev.candycanecazoo.packapunch.game.Cheats.set(player.getUUID(), dev.candycanecazoo.packapunch.game.Cheats.Cheat.GOD, false);
        }
        helper.succeed();
    }
}
