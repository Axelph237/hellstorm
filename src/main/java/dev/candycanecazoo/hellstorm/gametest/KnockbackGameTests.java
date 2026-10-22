package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** A player in a game is knocked back {@code 1 - players.knockback_reduction} as far as one who isn't. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnockbackGameTests {
    private KnockbackGameTests() {}

    private static double pushed(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(0.8, 1, 0);
        return -player.getDeltaMovement().x;
    }

    @GameTest(template = "floor", batch = "knockback")
    public static void aPlayerInAGameTakesFifteenPercentOfTheKnockback(GameTestHelper helper) {
        ServerPlayer outside = PurchaseGameTests.player(helper, "pap_knockback_outside");
        ServerPlayer inside = PurchaseGameTests.player(helper, "pap_knockback_inside");
        double full = pushed(outside);
        GameManager.Active active = PurchaseGameTests.game(helper, inside);
        double reduced = pushed(inside);
        double kept = 1 - active.game().settings().players().knockbackReduction();
        GameManager.end(active);
        helper.assertTrue(full > 0.7, "a player outside a game should take the full push, not " + full);
        helper.assertTrue(Math.abs(reduced - full * kept) < 1e-6, "a player in a game should take " + kept + " of " + full + ", not " + reduced);
        helper.assertTrue(Math.abs(kept - 0.15) < 1e-9, "the default leaves 15%, not " + kept);
        helper.succeed();
    }
}
