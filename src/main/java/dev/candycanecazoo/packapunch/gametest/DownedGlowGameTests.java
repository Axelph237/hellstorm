package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.LifeState;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A downed player glows red for their teammates, and gets their own team back when they stand up. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DownedGlowGameTests {
    private DownedGlowGameTests() {}

    @GameTest(template = "floor", batch = "downs_glow", timeoutTicks = 200)
    public static void aDownedPlayerGlowsRedAndGetsTheirTeamBack(GameTestHelper helper) {
        ServerPlayer alice = PurchaseGameTests.player(helper, "pap_glow_alice");
        ServerPlayer bob = PurchaseGameTests.player(helper, "pap_glow_bob");
        Scoreboard scoreboard = alice.getScoreboard();
        PlayerTeam own = scoreboard.getPlayerTeam("pap_glow_blue") != null ? scoreboard.getPlayerTeam("pap_glow_blue") : scoreboard.addPlayerTeam("pap_glow_blue");
        scoreboard.addPlayerToTeam(alice.getScoreboardName(), own);
        GameManager.Active active = PurchaseGameTests.game(helper, List.of(alice, bob));
        active.game().start();
        helper.runAfterDelay(active.game().settings().rounds().countdownTicks() + 2, () -> {
            try {
                ForgeHooks.onLivingDamage(alice, alice.damageSources().generic(), alice.getHealth() + 100);
                helper.assertTrue(active.game().player(alice.getUUID()).map(s -> s.life() instanceof LifeState.Downed).orElse(false), "alice should be down");
                helper.assertTrue(alice.hasGlowingTag(), "a downed player should glow");
                PlayerTeam team = scoreboard.getPlayersTeam(alice.getScoreboardName());
                helper.assertTrue(team != null && team.getColor() == ChatFormatting.RED, "the glow should be red, team " + (team == null ? null : team.getName()));
            } finally {
                GameManager.end(active);
            }
            helper.assertFalse(alice.hasGlowingTag(), "standing up should end the glow");
            helper.assertTrue(scoreboard.getPlayersTeam(alice.getScoreboardName()) == own, "alice should be back on her own team");
            scoreboard.removePlayerTeam(own);
            helper.succeed();
        });
    }
}
