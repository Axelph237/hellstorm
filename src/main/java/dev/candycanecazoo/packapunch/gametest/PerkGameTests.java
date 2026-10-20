package dev.candycanecazoo.packapunch.gametest;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.game.GameHooks;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tacz.PerkGunEffects;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Buying perks from a machine with the default datapack perks. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerkGameTests {
    private static final ResourceLocation JUGGERNOG = PackAPunch.id("juggernog");
    private static final ResourceLocation MULE_KICK = PackAPunch.id("mule_kick");
    private static final ResourceLocation QUICK_REVIVE = PackAPunch.id("quick_revive");
    private static final ResourceLocation DOUBLE_TAP = PackAPunch.id("double_tap");
    private static final ResourceLocation SPEED_COLA = PackAPunch.id("speed_cola");
    private static final ResourceLocation AK47 = ResourceLocation.tryBuild("tacz", "ak47");
    private static final float BASE_HEALTH = 20;

    private PerkGameTests() {}

    private static BlockPos machine(GameTestHelper helper, ResourceLocation perk) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.PERK_MACHINE.get().defaultBlockState());
        sell(helper, at, perk);
        return at;
    }

    private static void sell(GameTestHelper helper, BlockPos at, ResourceLocation perk) {
        ((PerkMachineBlock.Entity) helper.getLevel().getBlockEntity(at)).setConfig(new PerkMachineBlock.Config(Optional.of(perk), Optional.empty()));
    }

    private static PlayerState state(GameManager.Active active, ServerPlayer player) {
        return active.game().player(player.getUUID()).orElseThrow();
    }

    private static boolean hasJuggernogModifier(ServerPlayer player) {
        return player.getAttribute(Attributes.MAX_HEALTH).getModifier(PerkDef.modifierId(JUGGERNOG, 0)) != null;
    }

    @GameTest(template = "floor", batch = "perk_juggernog")
    public static void juggernogRaisesMaxHealthOnceForItsCostUntilTheGameEnds(GameTestHelper helper) {
        BlockPos at = machine(helper, JUGGERNOG);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_jug");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        try {
            active.game().refund(player.getUUID(), 10_000);
            int start = PurchaseGameTests.points(active, player);
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getMaxHealth() == BASE_HEALTH + 20, "max health is " + player.getMaxHealth());
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 2500, "expected to pay 2500, have " + PurchaseGameTests.points(active, player));
            helper.assertTrue(state(active, player).perks().containsKey(JUGGERNOG), "the perk isn't recorded");
            helper.assertTrue(events.contains(new GameEvent.PerkBought(player.getUUID(), JUGGERNOG)), "no perk_bought event");

            FeatureBlock.use(player, at);
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 2500, "a second Juggernog was charged");
            helper.assertTrue(player.getMaxHealth() == BASE_HEALTH + 20, "a second Juggernog stacked: " + player.getMaxHealth());
        } finally {
            GameManager.end(active);
        }
        helper.assertFalse(hasJuggernogModifier(player), "the modifier outlived the game");
        helper.assertTrue(player.getMaxHealth() == BASE_HEALTH, "max health after the game is " + player.getMaxHealth());
        helper.assertTrue(player.getHealth() <= BASE_HEALTH, "health above the restored max");
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_machine_cost")
    public static void aMachineWithItsOwnCostChargesThatInsteadOfThePerks(GameTestHelper helper) {
        BlockPos at = machine(helper, JUGGERNOG);
        ((PerkMachineBlock.Entity) helper.getLevel().getBlockEntity(at)).setConfig(new PerkMachineBlock.Config(Optional.of(JUGGERNOG), Optional.of(700)));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_cost");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            int start = PurchaseGameTests.points(active, player);
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).perks().containsKey(JUGGERNOG), "Juggernog wasn't bought");
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 700, "expected to pay 700, have " + PurchaseGameTests.points(active, player));
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_limit")
    public static void theFifthPerkIsRefusedAtTheDefaultLimit(GameTestHelper helper) {
        BlockPos at = machine(helper, JUGGERNOG);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_limit");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 50_000);
            int limit = active.game().settings().players().perkLimit();
            List<ResourceLocation> perks = List.of(JUGGERNOG, PackAPunch.id("speed_cola"), DOUBLE_TAP, PackAPunch.id("stamin_up"), QUICK_REVIVE, MULE_KICK);
            for (ResourceLocation perk : perks.subList(0, limit)) {
                sell(helper, at, perk);
                FeatureBlock.use(player, at);
            }
            helper.assertTrue(state(active, player).perks().size() == limit, "bought " + state(active, player).perks().keySet());
            int before = PurchaseGameTests.points(active, player);
            sell(helper, at, perks.get(limit));
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).perks().size() == limit, "went past the limit: " + state(active, player).perks().keySet());
            helper.assertTrue(PurchaseGameTests.points(active, player) == before, "charged for a refused perk");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_power")
    public static void powerGatedPerksWaitForThePower(GameTestHelper helper) {
        BlockPos at = machine(helper, JUGGERNOG);
        BlockPos lever = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(lever, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_power");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at, lever);
        try {
            active.game().refund(player.getUUID(), 10_000);
            int start = PurchaseGameTests.points(active, player);
            FeatureBlock.use(player, at);
            helper.assertFalse(state(active, player).perks().containsKey(JUGGERNOG), "Juggernog sold without power");
            helper.assertTrue(PurchaseGameTests.points(active, player) == start, "charged without power");

            sell(helper, at, QUICK_REVIVE);
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).perks().containsKey(QUICK_REVIVE), "Quick Revive doesn't need power");

            active.game().turnPowerOn(Optional.empty());
            sell(helper, at, JUGGERNOG);
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).perks().containsKey(JUGGERNOG), "Juggernog refused with the power on");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_mule_kick")
    public static void muleKickAddsAWeaponSlotUntilThePlayerDies(GameTestHelper helper) {
        BlockPos at = machine(helper, MULE_KICK);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_mule");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            sell(helper, at, MULE_KICK);
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).bonusWeaponSlots() == 1, "bonus slots " + state(active, player).bonusWeaponSlots());
            sell(helper, at, JUGGERNOG);
            FeatureBlock.use(player, at);

            GameHooks.onDeath(new LivingDeathEvent(player, player.damageSources().generic()));
            helper.assertTrue(state(active, player).perks().isEmpty(), "perks kept after dying: " + state(active, player).perks().keySet());
            helper.assertTrue(state(active, player).bonusWeaponSlots() == 0, "the slot outlived Mule Kick");
            helper.assertFalse(hasJuggernogModifier(player), "Juggernog's modifier outlived dying");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_double_tap")
    public static void doubleTapRaisesTheHeldGunsFireRate(GameTestHelper helper) {
        BlockPos at = machine(helper, DOUBLE_TAP);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_tap");
        player.setItemInHand(InteractionHand.MAIN_HAND, TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "ak47"), 1)
                .orElseThrow(() -> new IllegalStateException("tacz:ak47 missing")));
        PerkGunEffects.refresh(player);
        int base = rpm(player);
        helper.assertTrue(base > 0, "no cached rpm for the AK");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            FeatureBlock.use(player, at);
            int expected = (int) Math.round(base * 1.33);
            helper.assertTrue(rpm(player) == expected, "rpm " + rpm(player) + ", expected " + expected + " from " + base);
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(rpm(player) == base, "rpm after the game is " + rpm(player) + ", not " + base);
        helper.succeed();
    }

    /**
     * Drives TacZ's own reload: {@code reload()} starts it as the reload key does, then the start is moved back by
     * three quarters of the AK's empty-reload feed time and TacZ ticks it once, as the player's tick would.
     */
    @GameTest(template = "floor", batch = "perk_speed_cola")
    public static void speedColaFinishesFeedingAReloadInHalfTheTime(GameTestHelper helper) {
        BlockPos at = machine(helper, SPEED_COLA);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_cola");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            long feed = (long) (TimelessAPI.getCommonGunIndex(AK47).orElseThrow().getGunData().getReloadData().getFeed().getEmptyTime() * 1000);
            long elapsed = feed * 3 / 4;
            Reloaded before = reloadAfter(player, elapsed);
            helper.assertTrue(before.state() == ReloadState.StateType.EMPTY_RELOAD_FEEDING && before.magazine() == 0,
                    "without Speed Cola, " + elapsed + " of " + feed + " ms in should still be feeding, was " + before);

            active.game().refund(player.getUUID(), 10_000);
            FeatureBlock.use(player, at);
            helper.assertTrue(state(active, player).perks().containsKey(SPEED_COLA), "Speed Cola wasn't bought");
            Reloaded after = reloadAfter(player, elapsed);
            helper.assertTrue(after.state() != ReloadState.StateType.EMPTY_RELOAD_FEEDING && after.magazine() > 0,
                    "with Speed Cola, " + elapsed + " of " + feed + " ms in should have fed the magazine, was " + after);
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    private record Reloaded(ReloadState.StateType state, int magazine) {}

    private static Reloaded reloadAfter(ServerPlayer player, long elapsedMillis) {
        ItemStack gun = TaczBridge.createGun(AK47, 2).orElseThrow(() -> new IllegalStateException("tacz:ak47 missing"));
        IGun iGun = IGun.getIGunOrNull(gun);
        iGun.setCurrentAmmoCount(gun, 0);
        iGun.setBulletInBarrel(gun, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        operator.initialData();
        ShooterDataHolder data = operator.getDataHolder();
        // Long ago, so neither the last shot nor drawing the gun blocks the reload.
        data.shootTimestamp = -1_000_000L;
        data.drawTimestamp = 0;
        operator.reload();
        if (!data.reloadStateType.isReloading()) {
            throw new IllegalStateException("TacZ didn't start the reload: " + data.reloadStateType + ", can reload "
                    + ((AbstractGunItem) gun.getItem()).canReload(player, gun) + ", reserve " + iGun.getDummyAmmoAmount(gun)
                    + ", needs ammo " + operator.needCheckAmmo() + ", bolting " + data.isBolting);
        }
        data.reloadTimestamp -= elapsedMillis;
        ReloadState state = ((AbstractGunItem) gun.getItem()).tickReload(data, gun, player);
        return new Reloaded(state.getStateType(), iGun.getCurrentAmmoCount(gun));
    }

    private static int rpm(ServerPlayer player) {
        Integer rpm = IGunOperator.fromLivingEntity(player).getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE);
        return rpm == null ? -1 : rpm;
    }
}
