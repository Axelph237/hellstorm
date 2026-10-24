package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.perk.PerkEffects;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.game;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Buying from wall-buys: the first purchase, each repurchase rule, and the weapon-slot limit. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallBuyGameTests {
    private static final ResourceLocation AK47 = ResourceLocation.tryBuild("tacz", "ak47");
    private static final ResourceLocation M4A1 = ResourceLocation.tryBuild("tacz", "m4a1");
    private static final ResourceLocation GLOCK = ResourceLocation.tryBuild("tacz", "glock_17");
    // TacZ's own NBT keys, so the test can empty a gun without going through TacZ.
    private static final String MAGAZINE = "GunCurrentAmmoCount";
    private static final String RESERVE = "DummyAmmo";
    private static final String MAX_RESERVE = "MaxDummyAmmo";
    private static final String BARREL = "HasBulletInBarrel";

    private WallBuyGameTests() {}

    private static BlockPos wallBuy(GameTestHelper helper, WallBuy.Config config) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.WALL_BUY.get().defaultBlockState().setValue(WallBuyBlock.FACING, Direction.NORTH));
        entity(helper, at).setConfig(config);
        return at;
    }

    private static WallBuyBlock.Entity entity(GameTestHelper helper, BlockPos at) {
        return (WallBuyBlock.Entity) helper.getLevel().getBlockEntity(at);
    }

    private static List<ItemStack> guns(ServerPlayer player, ResourceLocation gunId) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (TaczBridge.inspect(stack).map(info -> info.gunId().equals(gunId)).orElse(false)) {
                found.add(stack);
            }
        }
        return found;
    }

    private static void assertPoints(GameTestHelper helper, GameManager.Active active, ServerPlayer player, int expected, String why) {
        int actual = points(active, player);
        helper.assertTrue(actual == expected, why + ": expected " + expected + " points, have " + actual);
    }

    /** What using {@code at} would do for {@code player}, as their prompt shows it. */
    private static WallBuy.Action action(GameTestHelper helper, BlockPos at, ServerPlayer player) {
        WallBuyBlock.Entity entity = entity(helper, at);
        return WallBuyBlock.action(entity.config(), entity.display(), player, WallBuyBlock.find(player.getInventory(), entity.config().item()));
    }

    @GameTest(template = "floor", batch = "wallbuy_gun")
    public static void aGunWallBuySellsTheGunThenRefillsItsAmmo(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 300, WallBuy.Repurchase.REFILL_AMMO, 100, 50, false));
        ServerPlayer player = player(helper, "pap_wallbuy_gun");
        GameManager.Active active = game(helper, player, at);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        try {
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, AK47).size() == 1, "buying gives one AK-47");
            assertPoints(helper, active, player, 200, "the gun costs 300");
            helper.assertTrue(events.contains(new GameEvent.WallBuy(player.getUUID(), AK47.toString(), 300, false)), "a wall_buy event, got " + events);

            helper.assertTrue(action(helper, at, player) instanceof WallBuy.Action.Deny, "a full gun gets no prompt, got " + action(helper, at, player));
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 200, "a full gun isn't refilled or charged");
            helper.assertFalse(events.stream().anyMatch(e -> e instanceof GameEvent.WallBuy buy && buy.refill()), "no refill event for a full gun");

            ItemStack gun = guns(player, AK47).get(0);
            gun.getTag().putInt(MAGAZINE, 0);
            gun.getTag().putInt(RESERVE, 0);
            gun.getTag().putBoolean(BARREL, false);
            helper.assertTrue(action(helper, at, player) instanceof WallBuy.Action.Refill, "an empty gun is offered a refill");
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 100, "a refill costs the ammo cost");
            helper.assertTrue(guns(player, AK47).size() == 1, "a refill doesn't give a second gun");
            gun = guns(player, AK47).get(0);
            helper.assertTrue(gun.getTag().getInt(MAGAZINE) > 0 && gun.getTag().getInt(RESERVE) > 0 && gun.getTag().getBoolean(BARREL),
                    "the refill fills magazine and reserve and chambers a round: " + gun.getTag());
            helper.assertTrue(TaczBridge.ammoFull(gun), "a refilled gun is full: " + gun.getTag());
            helper.assertTrue(events.contains(new GameEvent.WallBuy(player.getUUID(), AK47.toString(), 100, true)), "a refill event");
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 100, "refilling again straight away charges nothing");

            CompoundTag pap = gun.getOrCreateTagElement(ItemSpec.PAP);
            pap.putInt("tier", 1);
            gun.getTag().putInt(MAGAZINE, 0);
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 50, "an upgraded gun refills at the upgraded ammo cost");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_unlimited")
    public static void aGunWithUnlimitedAmmoIsNotRefilled(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 300, WallBuy.Repurchase.REFILL_AMMO, 100, 50, false));
        ServerPlayer player = player(helper, "pap_wallbuy_unlimited");
        GameManager.Active active = game(helper, player, at);
        try {
            ItemStack gun = TaczBridge.createGun(AK47, 1).orElseThrow();
            TaczBridge.setUnlimitedAmmo(gun, true);
            gun.getTag().putInt(MAGAZINE, 0);
            player.getInventory().setItem(player.getInventory().selected, gun);
            helper.assertTrue(action(helper, at, player) instanceof WallBuy.Action.Deny, "an unlimited gun gets no prompt, got " + action(helper, at, player));
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 500, "an unlimited gun isn't charged for a refill");
            helper.assertTrue(guns(player, AK47).get(0).getTag().getInt(MAGAZINE) == 0, "nor refilled: its magazine reloads from the endless reserve");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_item_fed")
    public static void aGunFedByAmmoItemsRefillsOnlyItsMagazine(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 300, WallBuy.Repurchase.REFILL_AMMO, 100, 50, false));
        ServerPlayer player = player(helper, "pap_wallbuy_item_fed");
        GameManager.Active active = game(helper, player, at);
        try {
            ItemStack gun = TaczBridge.createGun(AK47, 1).orElseThrow();
            gun.getTag().remove(RESERVE);
            gun.getTag().remove(MAX_RESERVE);
            gun.getTag().putInt(MAGAZINE, 0);
            player.getInventory().setItem(player.getInventory().selected, gun);
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 400, "a refill costs the ammo cost");
            gun = guns(player, AK47).get(0);
            helper.assertTrue(gun.getTag().getInt(MAGAZINE) > 0, "the magazine is filled: " + gun.getTag());
            helper.assertFalse(gun.getTag().contains(RESERVE), "a gun fed by ammo items gets no dummy reserve: " + gun.getTag());
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 400, "with its magazine full, it's full");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /** A wall-buy the game's map doesn't have, such as one left by the map imported before, sells nothing. */
    @GameTest(template = "floor", batch = "wallbuy_not_in_map")
    public static void aWallBuyNotInTheGamesMapSellsNothing(GameTestHelper helper) {
        ItemSpec apples = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3, Optional.empty());
        BlockPos at = wallBuy(helper, new WallBuy.Config(apples, 100, WallBuy.Repurchase.TOP_UP, 0, 0, false));
        ServerPlayer player = player(helper, "pap_wallbuy_stray");
        GameManager.Active active = game(helper, player);
        try {
            int before = points(active, player);
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, before, "a wall-buy outside the map shouldn't charge");
            helper.assertFalse(player.getInventory().items.stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.GOLDEN_APPLE)),
                    "a wall-buy outside the map shouldn't sell");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_top_up")
    public static void aTopUpWallBuyFillsTheStackBackToItsCount(GameTestHelper helper) {
        ItemSpec apples = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3, Optional.empty());
        BlockPos at = wallBuy(helper, new WallBuy.Config(apples, 100, WallBuy.Repurchase.TOP_UP, 0, 0, false));
        ServerPlayer player = player(helper, "pap_wallbuy_apples");
        GameManager.Active active = game(helper, player, at);
        try {
            player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.GOLDEN_APPLE) == 3, "topped up to 3, have " + player.getInventory().countItem(Items.GOLDEN_APPLE));
            assertPoints(helper, active, player, 400, "a top-up costs the price");
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.GOLDEN_APPLE) == 3, "a full stack isn't topped up");
            assertPoints(helper, active, player, 400, "a refused top-up charges nothing");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_deny")
    public static void aDenyWallBuySellsOnceAndRefusesWithoutCharging(GameTestHelper helper) {
        ItemSpec sword = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "iron_sword"), 1, Optional.empty());
        BlockPos at = wallBuy(helper, new WallBuy.Config(sword, 100, WallBuy.Repurchase.DENY, 0, 0, false));
        ServerPlayer player = player(helper, "pap_wallbuy_deny");
        GameManager.Active active = game(helper, player, at);
        // The starting loadout's knife is an iron sword too.
        player.getInventory().clearContent();
        try {
            entity(helper, at).setConfig(new WallBuy.Config(sword, 501, WallBuy.Repurchase.DENY, 0, 0, false));
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 0, "sold without enough points");
            assertPoints(helper, active, player, 500, "too few points charges nothing");

            entity(helper, at).setConfig(new WallBuy.Config(sword, 100, WallBuy.Repurchase.DENY, 0, 0, false));
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 1, "the first purchase gives the sword");
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 1, "DENY doesn't sell a second one");
            assertPoints(helper, active, player, 400, "DENY charges nothing");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_slots")
    public static void atTheWeaponSlotLimitBuyingReplacesTheHeldGun(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 100, WallBuy.Repurchase.REFILL_AMMO, 50, 50, false));
        ServerPlayer player = player(helper, "pap_wallbuy_slots");
        GameManager.Active active = game(helper, player, at);
        try {
            player.getInventory().setItem(0, TaczBridge.createGun(M4A1, 1).orElseThrow());
            player.getInventory().setItem(1, TaczBridge.createGun(GLOCK, 1).orElseThrow());
            player.getInventory().selected = 0;
            FeatureBlock.use(player, at);
            helper.assertTrue(TaczBridge.inspect(player.getInventory().getItem(0)).map(i -> i.gunId().equals(AK47)).orElse(false),
                    "the held M4A1 is swapped for the AK-47");
            helper.assertTrue(guns(player, M4A1).isEmpty() && guns(player, GLOCK).size() == 1, "the other gun stays, the held one goes");
            assertPoints(helper, active, player, 400, "the swap costs the price");

            entity(helper, at).setConfig(new WallBuy.Config(ItemSpec.Gun.of(M4A1), 100, WallBuy.Repurchase.REFILL_AMMO, 50, 50, false));
            player.getInventory().selected = 5;
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, M4A1).isEmpty(), "not holding a gun at the limit is refused");
            assertPoints(helper, active, player, 400, "a refused swap charges nothing");

            player.addEffect(new MobEffectInstance(PerkEffects.OFF_HAND_PERK.get(), MobEffectInstance.INFINITE_DURATION));
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, M4A1).size() == 1 && guns(player, AK47).size() == 1 && guns(player, GLOCK).size() == 1,
                    "Off-Hand's extra slot holds a third gun");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /** Outside a game, a player who can't open a machine's settings screen gets nothing from using it: no chat, no screen. */
    @GameTest(template = "floor", batch = "wallbuy_outside_game")
    public static void usingAMachineOutsideAGameAsANonOpSaysNothing(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 300, WallBuy.Repurchase.REFILL_AMMO, 100, 50, false));
        List<net.minecraft.network.chat.Component> said = new java.util.ArrayList<>();
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "pap_wallbuy_non_op")) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                said.add(message);
            }
        };
        FeatureBlock.use(player, at);
        helper.assertTrue(said.isEmpty(), "a non-op using a machine outside a game should see nothing, got " + said);
        helper.succeed();
    }
}
