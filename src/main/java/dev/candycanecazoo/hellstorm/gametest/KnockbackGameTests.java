package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.util.ExplodeUtil;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A player in a game is knocked back {@code 1 - players.knockback_reduction} as far as one who isn't, and a gun's explosion
 * pushes nothing during a match.
 */
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

    /**
     * How far a TacZ explosion 1.5 blocks to one side pushes a zombie away. A zombie, since TacZ pushes only what the blast
     * hurt and a fake player can't be hurt; the flag that stops the push also stops a player's.
     */
    private static double blownAway(GameTestHelper helper, ServerPlayer owner) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, TestLayout.ROOM_Y, 4));
        zombie.setNoAi(true);
        zombie.setDeltaMovement(Vec3.ZERO);
        ExplodeUtil.createExplosion(owner, owner, 8, 3, true, false, zombie.position().add(-1.5, 0.5, 0));
        double pushed = zombie.getDeltaMovement().x;
        zombie.discard();
        return pushed;
    }

    @GameTest(template = "floor", batch = "knockback_explosion")
    public static void aGunsExplosionPushesNothingDuringAMatch(GameTestHelper helper) {
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_knockback_blast");
        double before = blownAway(helper, player);
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        double during = blownAway(helper, player);
        GameManager.end(active);
        double after = blownAway(helper, player);
        helper.assertTrue(before > 0, "outside a match the blast should push the zombie away, not " + before);
        helper.assertTrue(during == 0, "during a match the blast should push nothing, not " + during);
        helper.assertTrue(after > 0, "once the match ends the blast should push again, not " + after);
        helper.succeed();
    }
}
