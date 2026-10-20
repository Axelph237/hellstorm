package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.LifeState;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Going down and getting back up, on real players: the crawl, the pistol, losing perks, and a teammate's revive. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DownsGameTests {
    private static final ResourceLocation JUGGERNOG = PackAPunch.id("juggernog");

    private DownsGameTests() {}

    private static boolean downed(GameManager.Active active, ServerPlayer player) {
        return active.game().player(player.getUUID()).map(s -> s.life() instanceof LifeState.Downed).orElse(false);
    }

    @GameTest(template = "floor", batch = "downs_revive", timeoutTicks = 300)
    public static void lethalDamageDownsAPlayerAndATeammateRevivesThem(GameTestHelper helper) {
        BlockPos machine = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(machine, PapRegistries.PERK_MACHINE.get().defaultBlockState());
        ((PerkMachineBlock.Entity) helper.getLevel().getBlockEntity(machine)).setConfig(new PerkMachineBlock.Config(Optional.of(JUGGERNOG), Optional.empty()));
        ServerPlayer alice = PurchaseGameTests.player(helper, "pap_downs_alice");
        ServerPlayer bob = PurchaseGameTests.player(helper, "pap_downs_bob");
        GameManager.Active active = PurchaseGameTests.game(helper, java.util.List.of(alice, bob), machine);
        bob.moveTo(alice.position().add(1, 0, 0));
        float baseHealth = alice.getMaxHealth();
        double baseSpeed = alice.getAttributeValue(Attributes.MOVEMENT_SPEED);
        active.game().refund(alice.getUUID(), 10_000);
        FeatureBlock.use(alice, machine);
        boolean bought = alice.getMaxHealth() > baseHealth;
        active.game().start();
        // However the test ends, the game mustn't outlive it.
        helper.runAfterDelay(290, () -> GameManager.get(helper.getLevel()).filter(a -> a == active).ifPresent(GameManager::end));
        helper.runAfterDelay(active.game().settings().rounds().countdownTicks() + 2, () -> {
            try {
                bob.moveTo(alice.position().add(1, 0, 0));
                helper.assertTrue(bought, "Juggernog wasn't bought");

                boolean cancelled = ForgeHooks.onLivingDamage(alice, alice.damageSources().generic(), alice.getHealth() + 100) == 0;
                helper.assertTrue(cancelled, "lethal damage should be cancelled");
                helper.assertTrue(downed(active, alice), "alice should be down");
                helper.assertTrue(alice.isAlive() && alice.getHealth() > 0, "alice died");
                helper.assertTrue(alice.getForcedPose() == Pose.SWIMMING, "alice should crawl, pose " + alice.getForcedPose());
                helper.assertTrue(alice.getAttributeValue(Attributes.MOVEMENT_SPEED) < baseSpeed, "alice should be slowed");
                helper.assertTrue(TaczBridge.inspect(alice.getMainHandItem()).map(i -> "pistol".equals(i.type())).orElse(false),
                        "alice should hold a pistol, holds " + alice.getMainHandItem());
                helper.assertTrue(alice.getMaxHealth() == baseHealth, "going down should take Juggernog, max health " + alice.getMaxHealth());
                helper.assertFalse(active.game().targetable(alice.getUUID()), "zombies should ignore a downed player");
            } catch (RuntimeException e) {
                GameManager.end(active);
                throw e;
            }
            // succeedWhen runs every tick until it passes, so it doubles as bob holding use.
            helper.succeedWhen(() -> {
                active.game().holdRevive(bob.getUUID(), alice.getUUID());
                try {
                    helper.assertFalse(downed(active, alice), "still down: " + active.game().player(alice.getUUID()).map(st -> st.life()).orElse(null)
                            + ", " + active.game().phase() + ", distance " + active.world().distance(bob.getUUID(), alice.getUUID()));
                    helper.assertTrue(alice.getForcedPose() == null, "alice should stand up");
                    helper.assertTrue(alice.getAttributeValue(Attributes.MOVEMENT_SPEED) == baseSpeed, "the slow should be gone");
                    helper.assertTrue(Math.abs(alice.getHealth() - alice.getMaxHealth() / 2) < 1.5f, "revived at half health, has " + alice.getHealth());
                    helper.assertTrue(alice.getInventory().items.stream().noneMatch(stack -> TaczBridge.inspect(stack).isPresent()), "the lent pistol should be taken back");
                } finally {
                    if (!downed(active, alice)) {
                        GameManager.end(active);
                    }
                }
            });
        });
    }
}
