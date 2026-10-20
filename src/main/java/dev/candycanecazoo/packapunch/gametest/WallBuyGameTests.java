package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.WallBuy;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.game;
import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Buying from wall-buys: the first purchase, each repurchase rule, and the weapon-slot limit. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallBuyGameTests {
    private static final ResourceLocation AK47 = ResourceLocation.tryBuild("tacz", "ak47");
    private static final ResourceLocation M4A1 = ResourceLocation.tryBuild("tacz", "m4a1");
    private static final ResourceLocation GLOCK = ResourceLocation.tryBuild("tacz", "glock_17");
    // TacZ's own NBT keys, so the test can empty a gun without going through TacZ.
    private static final String MAGAZINE = "GunCurrentAmmoCount";
    private static final String RESERVE = "DummyAmmo";

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

    @GameTest(template = "floor", batch = "wallbuy_gun")
    public static void aGunWallBuySellsTheGunThenRefillsItsAmmo(GameTestHelper helper) {
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 300, WallBuy.Repurchase.REFILL_AMMO, 100, 50));
        ServerPlayer player = player(helper, "pap_wallbuy_gun");
        GameManager.Active active = game(helper, player, at);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        try {
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, AK47).size() == 1, "buying gives one AK-47");
            assertPoints(helper, active, player, 200, "the gun costs 300");
            helper.assertTrue(events.contains(new GameEvent.WallBuy(player.getUUID(), AK47.toString(), 300, false)), "a wall_buy event, got " + events);

            ItemStack gun = guns(player, AK47).get(0);
            gun.getTag().putInt(MAGAZINE, 0);
            gun.getTag().putInt(RESERVE, 0);
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 100, "a refill costs the ammo cost");
            helper.assertTrue(guns(player, AK47).size() == 1, "a refill doesn't give a second gun");
            gun = guns(player, AK47).get(0);
            helper.assertTrue(gun.getTag().getInt(MAGAZINE) > 0 && gun.getTag().getInt(RESERVE) > 0,
                    "the refill fills magazine and reserve: " + gun.getTag());
            helper.assertTrue(events.contains(new GameEvent.WallBuy(player.getUUID(), AK47.toString(), 100, true)), "a refill event");

            CompoundTag pap = gun.getOrCreateTagElement(ItemSpec.PAP);
            pap.putInt("tier", 1);
            FeatureBlock.use(player, at);
            assertPoints(helper, active, player, 50, "a Pack-a-Punched gun refills at the PaP ammo cost");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "wallbuy_top_up")
    public static void aTopUpWallBuyFillsTheStackBackToItsCount(GameTestHelper helper) {
        ItemSpec apples = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3, Optional.empty());
        BlockPos at = wallBuy(helper, new WallBuy.Config(apples, 100, WallBuy.Repurchase.TOP_UP, 0, 0));
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
        BlockPos at = wallBuy(helper, new WallBuy.Config(sword, 100, WallBuy.Repurchase.DENY, 0, 0));
        ServerPlayer player = player(helper, "pap_wallbuy_deny");
        GameManager.Active active = game(helper, player, at);
        try {
            entity(helper, at).setConfig(new WallBuy.Config(sword, 501, WallBuy.Repurchase.DENY, 0, 0));
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 0, "sold without enough points");
            assertPoints(helper, active, player, 500, "too few points charges nothing");

            entity(helper, at).setConfig(new WallBuy.Config(sword, 100, WallBuy.Repurchase.DENY, 0, 0));
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
        BlockPos at = wallBuy(helper, new WallBuy.Config(ItemSpec.Gun.of(AK47), 100, WallBuy.Repurchase.REFILL_AMMO, 50, 50));
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

            entity(helper, at).setConfig(new WallBuy.Config(ItemSpec.Gun.of(M4A1), 100, WallBuy.Repurchase.REFILL_AMMO, 50, 50));
            player.getInventory().selected = 5;
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, M4A1).isEmpty(), "not holding a gun at the limit is refused");
            assertPoints(helper, active, player, 400, "a refused swap charges nothing");

            active.game().player(player.getUUID()).orElseThrow().setBonusWeaponSlots(1);
            FeatureBlock.use(player, at);
            helper.assertTrue(guns(player, M4A1).size() == 1 && guns(player, AK47).size() == 1 && guns(player, GLOCK).size() == 1,
                    "a bonus slot (Mule Kick) holds a third gun");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }
}
