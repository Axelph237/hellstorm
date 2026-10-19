package dev.candycanecazoo.packapunch.gametest;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 0 spikes against TacZ and vanilla APIs. TacZ's draw, reload, and shoot timers use wall-clock time,
 * and the GameTest server ticks faster than real time, so these tests poll every tick instead of waiting a fixed number of ticks.
 */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaczSpikeTests {
    private static final int LONG_TIMEOUT = 20_000;

    private TaczSpikeTests() {}

    private static Zombie gunHolder(GameTestHelper helper, BlockPos pos, ItemStack gun) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        zombie.setNoAi(true);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, gun);
        IGunOperator operator = IGunOperator.fromLivingEntity(zombie);
        operator.initialData();
        operator.draw(zombie::getMainHandItem);
        return zombie;
    }

    private static FeedType feedType(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId).orElseThrow().getGunData().getReloadData().getType();
    }

    @GameTest(template = "floor", timeoutTicks = LONG_TIMEOUT)
    public static void dummyAmmoReloadsEveryMagazineGun(GameTestHelper helper) {
        List<ResourceLocation> guns = TaczBridge.allGuns();
        helper.assertTrue(!guns.isEmpty(), "TacZ reported no guns");
        Map<ResourceLocation, Zombie> holders = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> startReserve = new LinkedHashMap<>();
        List<ResourceLocation> inventoryFed = new ArrayList<>();
        for (ResourceLocation gunId : guns) {
            if (feedType(gunId) == FeedType.INVENTORY) {
                inventoryFed.add(gunId);
                continue;
            }
            ItemStack stack = TaczBridge.createGun(gunId, 3).orElseThrow();
            IGun gun = IGun.getIGunOrNull(stack);
            gun.setCurrentAmmoCount(stack, 0);
            gun.setBulletInBarrel(stack, false);
            startReserve.put(gunId, gun.getDummyAmmoAmount(stack));
            int i = holders.size();
            holders.put(gunId, gunHolder(helper, new BlockPos(1 + i % 7, TestLayout.ROOM_Y, 1 + (i / 7) % 7), stack));
        }
        PackAPunch.LOG.info("[spike] inventory-fed guns skipped (dummy ammo does not apply): {}", inventoryFed);
        long startedAt = System.currentTimeMillis();
        helper.onEachTick(() -> holders.values().forEach(z -> IGunOperator.fromLivingEntity(z).reload()));
        helper.succeedWhen(() -> {
            List<String> pending = new ArrayList<>();
            holders.forEach((gunId, zombie) -> {
                ItemStack stack = zombie.getMainHandItem();
                IGun gun = IGun.getIGunOrNull(stack);
                if (gun.getCurrentAmmoCount(stack) <= 0 || gun.getDummyAmmoAmount(stack) >= startReserve.get(gunId)) {
                    pending.add(gunId.toString());
                }
            });
            if (!pending.isEmpty()) {
                if (System.currentTimeMillis() - startedAt > 30_000) {
                    helper.fail("not reloaded from dummy ammo after 30s wall time: " + pending);
                }
                helper.fail("still reloading: " + pending);
            }
            holders.forEach((gunId, zombie) -> {
                ItemStack stack = zombie.getMainHandItem();
                IGun gun = IGun.getIGunOrNull(stack);
                PackAPunch.LOG.info("[spike] dummy ammo ok: {} ammo={} reserve {}->{}", gunId,
                        gun.getCurrentAmmoCount(stack), startReserve.get(gunId), gun.getDummyAmmoAmount(stack));
            });
            PackAPunch.LOG.info("[spike] {} magazine-fed guns reloaded from dummy ammo in {}ms wall time",
                    holders.size(), System.currentTimeMillis() - startedAt);
        });
    }

    /**
     * Fires an unmodified AK and an AK overridden to 60 RPM, then fires both again after 300ms of wall time.
     * The stock gun (600 RPM, 100ms interval) must fire. The 60 RPM gun (1000ms interval) must still be cooling down.
     */
    @GameTest(template = "floor", timeoutTicks = LONG_TIMEOUT)
    public static void attachmentPropertyEventChangesRpmInLiveFire(GameTestHelper helper) {
        ResourceLocation gunId = ResourceLocation.tryBuild("tacz", "ak47");
        ItemStack slowStack = TaczBridge.createGun(gunId, 3).orElseThrow(() -> new IllegalStateException("tacz:ak47 missing"));
        slowStack.getOrCreateTag().putInt(SpikeRpmOverride.TAG, 60);
        Zombie stock = gunHolder(helper, new BlockPos(2, TestLayout.ROOM_Y, 4), TaczBridge.createGun(gunId, 3).orElseThrow());
        Zombie slow = gunHolder(helper, new BlockPos(6, TestLayout.ROOM_Y, 4), slowStack);
        AttachmentPropertyManager.postChangeEvent(slow, slow.getMainHandItem());
        AttachmentPropertyManager.postChangeEvent(stock, stock.getMainHandItem());
        Integer cachedRpm = IGunOperator.fromLivingEntity(slow).getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE);
        helper.assertTrue(cachedRpm != null && cachedRpm == 60, "cached RPM was " + cachedRpm + ", expected 60");

        long[] firstShotAt = {-1, -1};
        Zombie[] shooters = {stock, slow};
        helper.onEachTick(() -> {
            for (int i = 0; i < shooters.length; i++) {
                if (firstShotAt[i] < 0) {
                    ShootResult result = IGunOperator.fromLivingEntity(shooters[i]).shoot(() -> 0f, () -> 0f);
                    if (result == ShootResult.SUCCESS) {
                        firstShotAt[i] = System.currentTimeMillis();
                    } else if (result != ShootResult.IS_DRAWING && result != ShootResult.NOT_DRAW && result != ShootResult.COOL_DOWN) {
                        helper.fail("unexpected first shot result " + result);
                    }
                }
            }
            if (firstShotAt[0] < 0 || firstShotAt[1] < 0) {
                return;
            }
            long elapsed = System.currentTimeMillis() - Math.max(firstShotAt[0], firstShotAt[1]);
            if (elapsed < 300) {
                return;
            }
            ShootResult stockSecond = IGunOperator.fromLivingEntity(stock).shoot(() -> 0f, () -> 0f);
            ShootResult slowSecond = IGunOperator.fromLivingEntity(slow).shoot(() -> 0f, () -> 0f);
            PackAPunch.LOG.info("[spike] rpm override: {}ms after first shot, stock={} slow(60rpm)={}", elapsed, stockSecond, slowSecond);
            if (elapsed > 900) {
                helper.fail("ran past the 60 RPM window without a clean comparison");
            }
            helper.assertTrue(stockSecond == ShootResult.SUCCESS, "stock AK second shot " + stockSecond);
            helper.assertTrue(slowSecond == ShootResult.COOL_DOWN, "60 RPM AK second shot " + slowSecond);
            helper.succeed();
        });
    }

    @GameTest(template = "floor")
    public static void goalSelectorIsReachable(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, TestLayout.ROOM_Y, 4));
        helper.assertTrue(!zombie.goalSelector.getAvailableGoals().isEmpty(), "zombie started with no goals");
        zombie.goalSelector.removeAllGoals(goal -> true);
        zombie.targetSelector.removeAllGoals(goal -> true);
        helper.assertTrue(zombie.goalSelector.getAvailableGoals().isEmpty(), "goals not cleared");
        helper.succeed();
    }

    /** A fake player can't tick (no network connection), so this calls the pose update that Player.tick would call. */
    @GameTest(template = "floor")
    public static void forcedPoseMakesPlayerCrawl(GameTestHelper helper) throws ReflectiveOperationException {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_crawl_test"));
        Method updatePlayerPose = ObfuscationReflectionHelper.findMethod(Player.class, "m_7594_");
        player.setForcedPose(Pose.SWIMMING);
        updatePlayerPose.invoke(player);
        helper.assertTrue(player.getPose() == Pose.SWIMMING, "pose was " + player.getPose());
        helper.assertTrue(player.getBbHeight() < 1.0f, "crawl hitbox height was " + player.getBbHeight());
        player.setForcedPose(null);
        updatePlayerPose.invoke(player);
        helper.assertTrue(player.getPose() == Pose.STANDING, "pose after clearing was " + player.getPose());
        helper.succeed();
    }
}
