package dev.candycanecazoo.packapunch.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.game.ForgeGameWorld;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.GameMap;
import dev.candycanecazoo.packapunch.game.GamePhase;
import dev.candycanecazoo.packapunch.game.Loadout;
import dev.candycanecazoo.packapunch.game.RoundPlan;
import dev.candycanecazoo.packapunch.game.Sessions;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameLoopGameTests {
    private GameLoopGameTests() {}

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    @GameTest(template = "floor")
    public static void joiningAndLeavingRestoresTheExactPlayerState(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper, "pap_session_test");
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 17));
        player.getInventory().setItem(8, new ItemStack(Items.GOLDEN_APPLE, 3));
        player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000, 0));
        player.experienceLevel = 12;
        Sessions sessions = Sessions.get(helper.getLevel().getServer());
        sessions.save(player);
        Loadout.give(player, GameSettings.DEFAULT.players());
        player.setGameMode(GameType.ADVENTURE);
        player.experienceLevel = 0;
        helper.assertTrue(player.getInventory().getItem(0).getItem() != Items.DIAMOND, "loadout should replace the inventory");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "loadout should clear effects");
        sessions.save(player);
        helper.assertTrue(sessions.restore(player), "snapshot missing");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), new ItemStack(Items.DIAMOND, 17)), "slot 0 is " + player.getInventory().getItem(0));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(8), new ItemStack(Items.GOLDEN_APPLE, 3)), "slot 8 is " + player.getInventory().getItem(8));
        helper.assertTrue(player.getInventory().armor.get(3).is(Items.IRON_HELMET), "helmet missing");
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "loadout items leaked into the restored inventory");
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "effect missing");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "game mode is " + player.gameMode.getGameModeForPlayer());
        helper.assertTrue(player.experienceLevel == 12, "xp level is " + player.experienceLevel);
        helper.assertTrue(!sessions.has(player.getUUID()), "snapshot should be consumed by restore");
        helper.succeed();
    }

    /** A real game in the test area: one zone, one marker, one fake player. Runs through GameManager and the Forge hooks. */
    // Games are one per level, so tests that register one each get their own batch (batches run one after another).
    @GameTest(template = "floor", timeoutTicks = 400, batch = "game_melee")
    public static void aGameSpawnsScaledZombiesAndPaysForAMeleeKill(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        BlockPos max = helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8));
        BlockPos marker = helper.absolutePos(new BlockPos(7, ROOM_Y, 7));
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_arena"), List.of(BlockBox.of(min, max)), true))
                .withFeature(marker, FeatureKind.SPAWN);
        ServerPlayer player = fakePlayer(helper, "pap_loop_test");
        player.moveTo(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)).getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()),
                List.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1))), id -> id.equals(player.getUUID()) ? player : null);
        try {
            helper.assertTrue(active.game().join(player.getUUID()), "join failed");
            helper.assertTrue(active.game().start(), "start failed");
        } catch (RuntimeException e) {
            GameManager.unregister(helper.getLevel());
            throw e;
        }
        int startPoints = active.game().player(player.getUUID()).orElseThrow().points();
        AABB area = new AABB(min, max).inflate(2);
        helper.succeedWhen(() -> {
            List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet (phase " + active.game().phase() + ")");
            Zombie zombie = zombies.get(0);
            double expected = RoundPlan.zombieHealth(active.game().settings().rounds(), 1);
            if (Math.abs(zombie.getMaxHealth() - expected) > 1e-3) {
                GameManager.unregister(helper.getLevel());
                helper.fail("zombie max health " + zombie.getMaxHealth() + ", expected " + expected);
            }
            if (zombie.blockPosition().distManhattan(marker) > 2) {
                GameManager.unregister(helper.getLevel());
                helper.fail("zombie spawned at " + zombie.blockPosition() + ", marker is " + marker);
            }
            zombie.hurt(player.damageSources().playerAttack(player), 10_000f);
            int points = active.game().player(player.getUUID()).orElseThrow().points();
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
            GameManager.end(active);
            if (points != startPoints + active.game().settings().economy().meleeKill()) {
                helper.fail("points " + points + ", expected " + (startPoints + active.game().settings().economy().meleeKill()) + " for a melee kill");
            }
            if (!drops.isEmpty()) {
                helper.fail("game zombie dropped " + drops);
            }
            if (!(active.game().phase() instanceof GamePhase.GameOver)) {
                helper.fail("ending the game should leave it in GameOver, was " + active.game().phase());
            }
        });
    }

    /**
     * A fake player fires a real TacZ AK at a game zombie. Non-lethal hits pay the hit value through TacZ's Post event.
     * The finishing shot pays a kill. Draw and shoot timers are wall-clock, so the test polls.
     */
    @GameTest(template = "floor", timeoutTicks = 4000, batch = "game_gun")
    public static void gunHitsAndKillsPayThroughTacz(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        BlockPos max = helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8));
        BlockPos marker = helper.absolutePos(new BlockPos(7, ROOM_Y, 4));
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_range"), List.of(BlockBox.of(min, max)), true))
                .withFeature(marker, FeatureKind.SPAWN);
        ServerPlayer player = fakePlayer(helper, "pap_gun_test");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 4));
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        ItemStack gun = TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "ak47"), 4).orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        operator.initialData();
        operator.draw(player::getMainHandItem);
        AABB area = new AABB(min, max).inflate(2);
        int start = active.game().player(player.getUUID()).orElseThrow().points();
        int hitValue = active.game().settings().economy().hit();
        int[] hitsLanded = {0};
        Zombie[] target = {null};
        helper.onEachTick(() -> {
            if (target[0] == null) {
                List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
                if (zombies.isEmpty()) {
                    return;
                }
                target[0] = zombies.get(0);
                target[0].setNoAi(true);
                target[0].moveTo(helper.absolutePos(new BlockPos(6, ROOM_Y, 4)).getCenter().add(0, -0.5, 0));
                target[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                target[0].setHealth(1000);
            }
            player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target[0].getBoundingBox().getCenter());
            float pitch = player.getXRot(), yaw = player.getYRot();
            ShootResult result = operator.shoot(() -> pitch, () -> yaw);
            if (result == ShootResult.SUCCESS) {
                hitsLanded[0]++;
            } else if (result == ShootResult.NO_AMMO) {
                operator.reload();
            }
        });
        int[] pointsBeforeKill = {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(target[0] != null, "no zombie spawned yet");
            var state = active.game().player(player.getUUID()).orElseThrow();
            if (pointsBeforeKill[0] < 0) {
                helper.assertTrue(state.points() >= start + 3 * hitValue,
                        "only " + (state.points() - start) + " points from " + hitsLanded[0] + " shots so far");
                helper.assertTrue(state.kills() == 0, "the 1000 HP zombie died before it could be weakened");
                pointsBeforeKill[0] = state.points();
                target[0].setHealth(1);
            }
            helper.assertTrue(state.kills() == 1, "waiting for the finishing shot");
            int paid = state.points() - pointsBeforeKill[0];
            GameManager.end(active);
            int kill = active.game().settings().economy().kill();
            int headshot = active.game().settings().economy().headshotKill();
            if (paid != kill && paid != headshot) {
                helper.fail("the finishing shot paid " + paid + ", expected " + kill + " (body) or " + headshot + " (head)");
            }
        });
    }
}
