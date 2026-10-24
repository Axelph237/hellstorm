package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** With {@code players.friendly_fire} off, players in a game can't hurt each other; with it on, they can. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FriendlyFireGameTests {
    private FriendlyFireGameTests() {}

    /** Whether {@code source} gets through to {@code target}: the attack event isn't cancelled. */
    private static boolean hurts(ServerPlayer target, DamageSource source) {
        return !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(target, source, 4));
    }

    private static DamageSource shot(GameTestHelper helper, ServerPlayer shooter) {
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(shooter);
        return helper.getLevel().damageSources().arrow(arrow, shooter);
    }

    @GameTest(template = "floor", batch = "friendly_fire_off")
    public static void teammatesCantHurtEachOtherUnlessFriendlyFireIsOn(GameTestHelper helper) {
        ServerPlayer a = PurchaseGameTests.player(helper, "pap_ff_a");
        ServerPlayer b = PurchaseGameTests.player(helper, "pap_ff_b");
        ServerPlayer outsider = PurchaseGameTests.player(helper, "pap_ff_outsider");
        var damage = helper.getLevel().damageSources();
        helper.assertTrue(hurts(b, damage.playerAttack(a)), "outside a game, players hurt each other as usual");
        GameManager.Active active = PurchaseGameTests.game(helper, List.of(a, b));
        try {
            helper.assertFalse(active.game().settings().players().friendlyFire(), "friendly fire is off by default");
            helper.assertFalse(hurts(b, damage.playerAttack(a)), "a teammate's swing does nothing");
            helper.assertFalse(hurts(b, shot(helper, a)), "a teammate's shot does nothing");
            helper.assertTrue(hurts(a, damage.explosion(a, a)), "a player's own blast still hurts them");
            helper.assertTrue(hurts(b, damage.playerAttack(outsider)), "a player outside the game isn't a teammate");
        } finally {
            GameManager.end(active);
        }
        boolean was = ServerConfig.GAME.friendlyFire().get();
        ServerConfig.GAME.friendlyFire().set(true);
        try {
            GameManager.Active on = PurchaseGameTests.game(helper, List.of(a, b));
            try {
                helper.assertTrue(hurts(b, damage.playerAttack(a)), "with friendly fire on, a teammate's swing hurts");
                helper.assertTrue(hurts(b, shot(helper, a)), "with friendly fire on, a teammate's shot hurts");
            } finally {
                GameManager.end(on);
            }
        } finally {
            ServerConfig.GAME.friendlyFire().set(was);
        }
        helper.succeed();
    }
}
