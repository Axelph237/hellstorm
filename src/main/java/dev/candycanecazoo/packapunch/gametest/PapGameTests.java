package dev.candycanecazoo.packapunch.gametest;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.FeatureBlock;
import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.pap.PunchAudit;
import dev.candycanecazoo.packapunch.pap.PunchOutcome;
import dev.candycanecazoo.packapunch.pap.PunchResolver;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** The Pack-a-Punch machine, through the shared purchase path. Each test registers a game, so each has its own batch. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PapGameTests {
    private static final ResourceLocation AK = ResourceLocation.tryBuild("tacz", "ak47");

    private PapGameTests() {}

    private static BlockPos machine(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(at, PapRegistries.PAP_MACHINE.get().defaultBlockState());
        return at;
    }

    private static PapMachineBlock.Entity entity(GameTestHelper helper, BlockPos at) {
        return (PapMachineBlock.Entity) helper.getLevel().getBlockEntity(at);
    }

    private static ItemStack punchable(ItemStack stack, String punchId) {
        CompoundTag pap = stack.getOrCreateTagElement(ItemSpec.PAP);
        pap.putBoolean(ItemSpec.PUNCHABLE, true);
        if (punchId != null) {
            pap.putString(ItemSpec.PUNCH_ID, punchId);
        }
        return stack;
    }

    private static ItemStack ak() {
        return ItemSpec.Gun.of(AK).build(4).orElseThrow(() -> new IllegalStateException("tacz:ak47 missing"));
    }

    /** Runs a later step of a test, ending the game if it fails so the next batch starts clean. */
    private static void step(GameManager.Active active, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | AssertionError e) {
            GameManager.end(active);
            throw e;
        }
    }

    private static void assertRefusedAndKept(GameTestHelper helper, GameManager.Active active, ServerPlayer player, BlockPos at, ItemStack item, String what) {
        player.setItemInHand(InteractionHand.MAIN_HAND, item.copy());
        int points = PurchaseGameTests.points(active, player);
        FeatureBlock.use(player, at);
        helper.assertTrue(ItemStack.matches(item, player.getMainHandItem()), what + ": the item should come back unchanged, have " + player.getMainHandItem().getTag());
        helper.assertTrue(PurchaseGameTests.points(active, player) == points, what + ": charged for a refusal");
        helper.assertTrue(entity(helper, at).empty(), what + ": the machine took it");
    }

    @GameTest(template = "floor", timeoutTicks = 400, batch = "pap_gun")
    public static void punchingAGunChargesTakesItAndOffersTheUpgradeOnlyToItsOwner(GameTestHelper helper) {
        BlockPos at = machine(helper);
        ServerPlayer owner = PurchaseGameTests.player(helper, "pap_owner");
        ServerPlayer other = PurchaseGameTests.player(helper, "pap_other");
        GameManager.Active active = PurchaseGameTests.game(helper, owner, at);
        GameSettings.Pap settings = active.game().settings().pap();
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        int start;
        ItemStack otherGun = ak();
        try {
            active.game().join(other.getUUID());
            active.game().refund(owner.getUUID(), settings.defaultCost());
            active.game().refund(other.getUUID(), settings.defaultCost());
            start = PurchaseGameTests.points(active, owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, ak());
            FeatureBlock.use(owner, at);
            helper.assertTrue(PurchaseGameTests.points(active, owner) == start - settings.defaultCost(),
                    "expected to pay " + settings.defaultCost() + ", have " + PurchaseGameTests.points(active, owner) + " of " + start);
            helper.assertTrue(owner.getMainHandItem().isEmpty(), "the machine should take the gun");
            helper.assertTrue(events.contains(new GameEvent.PapUsed(owner.getUUID(), AK.toString())), "pap_used not emitted: " + events);

            int otherPoints = PurchaseGameTests.points(active, other);
            other.setItemInHand(InteractionHand.MAIN_HAND, otherGun.copy());
            FeatureBlock.use(other, at);
            helper.assertTrue(PurchaseGameTests.points(active, other) == otherPoints && ItemStack.matches(otherGun, other.getMainHandItem()),
                    "a busy machine should refuse other players and keep their gun");
            FeatureBlock.use(owner, at);
            helper.assertTrue(owner.getMainHandItem().isEmpty() && !entity(helper, at).ready(), "the gun can't be taken while it's processing");
        } catch (RuntimeException | AssertionError e) {
            GameManager.end(active);
            throw e;
        }
        int paid = start - settings.defaultCost();
        helper.runAfterDelay(settings.processTicks() + 1, () -> step(active, () -> {
            helper.assertTrue(entity(helper, at).ready(), "not ready after process_ticks");
            FeatureBlock.use(other, at);
            helper.assertTrue(ItemStack.matches(otherGun, other.getMainHandItem()), "only the owner can take the upgraded gun");
            helper.assertTrue(entity(helper, at).ready(), "another player took the upgraded gun");

            FeatureBlock.use(owner, at);
            ItemStack upgraded = owner.getMainHandItem();
            helper.assertTrue(TaczBridge.inspect(upgraded).map(info -> info.gunId().equals(AK)).orElse(false), "the owner didn't get the AK back");
            helper.assertTrue(entity(helper, at).empty(), "the machine should be empty after collecting");
            helper.assertTrue(PurchaseGameTests.points(active, owner) == paid, "collecting should be free");
            CompoundTag pap = upgraded.getTagElement(ItemSpec.PAP);
            helper.assertTrue(pap != null && pap.getInt(PunchResolver.TIER) == 1, "expected tier 1, have " + pap);
            helper.assertTrue(!pap.contains(ItemSpec.PUNCHABLE), "at max_tier 1 the gun is no longer punchable");
            helper.assertTrue(upgraded.getHoverName().getContents() instanceof TranslatableContents name && name.getKey().equals("item.packapunch.upgraded"),
                    "not renamed: " + upgraded.getHoverName());

            int stockMagazine = TaczBridge.inspect(ak()).orElseThrow().magazineSize();
            int magazine = TaczBridge.inspect(upgraded).orElseThrow().magazineSize();
            IGun gun = IGun.getIGunOrNull(upgraded);
            helper.assertTrue(magazine == Math.round(stockMagazine * settings.magazineMultiplier()), "magazine " + magazine + " from " + stockMagazine);
            helper.assertTrue(gun.getCurrentAmmoCount(upgraded) == magazine, "magazine not refilled: " + gun.getCurrentAmmoCount(upgraded));
            int stockReserve = IGun.getIGunOrNull(ak()).getMaxDummyAmmoAmount(ak());
            helper.assertTrue(gun.getMaxDummyAmmoAmount(upgraded) == Math.round(stockReserve * settings.magazineMultiplier())
                    && gun.getDummyAmmoAmount(upgraded) == gun.getMaxDummyAmmoAmount(upgraded), "reserve " + gun.getDummyAmmoAmount(upgraded));

            AttachmentCacheProperty stockCache = new AttachmentCacheProperty();
            stockCache.eval(ak(), TimelessAPI.getCommonGunIndex(AK).orElseThrow().getGunData());
            int stockRpm = stockCache.getCache(GunProperties.ROUNDS_PER_MINUTE);
            AttachmentCacheProperty cache = new AttachmentCacheProperty();
            cache.eval(upgraded, TimelessAPI.getCommonGunIndex(AK).orElseThrow().getGunData());
            MinecraftForge.EVENT_BUS.post(new AttachmentPropertyEvent(upgraded, cache));
            int rpm = cache.getCache(GunProperties.ROUNDS_PER_MINUTE);
            helper.assertTrue(rpm == Math.round(stockRpm * settings.rpmMultiplier()), "rpm " + rpm + " from " + stockRpm);

            EntityHurtByGunEvent.Pre hit = new EntityHurtByGunEvent.Pre(null, other, owner, AK, AK, 10f, null, false, 1f, LogicalSide.SERVER);
            MinecraftForge.EVENT_BUS.post(hit);
            helper.assertTrue(Math.abs(hit.getBaseAmount() - 10f * settings.damageMultiplier()) < 1e-4, "damage " + hit.getBaseAmount());
            EntityHurtByGunEvent.Pre stockHit = new EntityHurtByGunEvent.Pre(null, owner, other, AK, AK, 10f, null, false, 1f, LogicalSide.SERVER);
            MinecraftForge.EVENT_BUS.post(stockHit);
            helper.assertTrue(stockHit.getBaseAmount() == 10f, "a stock gun's damage changed: " + stockHit.getBaseAmount());

            ItemStack fullyUpgraded = upgraded.copy();
            FeatureBlock.use(owner, at);
            helper.assertTrue(ItemStack.matches(fullyUpgraded, owner.getMainHandItem()) && PurchaseGameTests.points(active, owner) == paid,
                    "a fully upgraded gun should be refused and kept");
        }));
        helper.runAfterDelay(settings.processTicks() + 2, () -> {
            GameManager.end(active);
            helper.succeed();
        });
    }

    @GameTest(template = "floor", batch = "pap_refuse")
    public static void refusedItemsComeBackUnchangedAndCostNothing(GameTestHelper helper) {
        BlockPos at = machine(helper);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_refused");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 100_000);
            assertRefusedAndKept(helper, active, player, at, new ItemStack(Items.IRON_SWORD), "a sword that isn't punchable");
            assertRefusedAndKept(helper, active, player, at, new ItemSpec.Gun(AK, false, false).build(4).orElseThrow(), "a gun that isn't punchable");
            assertRefusedAndKept(helper, active, player, at, punchable(new ItemStack(Items.STICK), null), "a punchable stick with no punch_id");
            assertRefusedAndKept(helper, active, player, at, punchable(new ItemStack(Items.STICK), "packapunch:no_such_upgrade"), "an unknown punch_id");
            assertRefusedAndKept(helper, active, player, at, ItemStack.EMPTY, "an empty hand");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "pap_power")
    public static void theMachineNeedsPower(GameTestHelper helper) {
        BlockPos at = machine(helper);
        BlockPos power = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(power, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_power");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at, power);
        try {
            active.game().refund(player.getUUID(), 100_000);
            assertRefusedAndKept(helper, active, player, at, ak(), "no power");
            active.game().turnPowerOn(Optional.empty());
            player.setItemInHand(InteractionHand.MAIN_HAND, ak());
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getMainHandItem().isEmpty() && !entity(helper, at).empty(), "with power the machine takes the gun");
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(entity(helper, at).empty(), "ending the game empties the machine");
        helper.succeed();
    }

    /** The shipped definitions and the default loadout are clean; live items are told apart by whether they are TacZ guns. */
    @GameTest(template = "floor")
    public static void theAuditChecksLoadedItems(GameTestHelper helper) {
        helper.assertTrue(PunchAudit.run(helper.getLevel()).isEmpty(), "shipped sources flagged: " + PunchAudit.run(helper.getLevel()));
        helper.assertTrue(GameManager.validationContext(helper.getLevel()).itemProblems().isEmpty(), "map validation got item problems");
        ItemStack gun = ak();
        ItemSpec gunAsItem = new ItemSpec.Plain(ForgeRegistries.ITEMS.getKey(gun.getItem()), 1, Optional.of(gun.getTag()));
        ItemSpec stick = new ItemSpec.Plain(ForgeRegistries.ITEMS.getKey(Items.STICK), 1, Optional.of(punchable(new ItemStack(Items.STICK), null).getTag()));
        List<PunchAudit.Finding> findings = PunchAudit.check(List.of(
                new PunchAudit.Entry("gun", Optional.empty(), gunAsItem), new PunchAudit.Entry("stick", Optional.empty(), stick)));
        helper.assertTrue(findings.size() == 1 && findings.get(0).entry().source().equals("stick")
                && findings.get(0).reason() == PunchOutcome.RejectReason.NO_UPGRADE_PATH, "findings " + findings);
        helper.succeed();
    }

    /** Uses the shipped default {@code packapunch:iron_sword}: an iron sword becomes a Sharpness III diamond sword for 2500. */
    @GameTest(template = "floor", timeoutTicks = 800, batch = "pap_replace")
    public static void aPunchDefReplacesTheItemAndAnUncollectedResultIsLost(GameTestHelper helper) {
        BlockPos at = machine(helper);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_replace");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        GameSettings.Pap settings = active.game().settings().pap();
        int start;
        try {
            active.game().refund(player.getUUID(), 100_000);
            start = PurchaseGameTests.points(active, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, punchable(new ItemStack(Items.IRON_SWORD), "packapunch:iron_sword"));
            FeatureBlock.use(player, at);
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 2500, "the def's own cost applies, have " + PurchaseGameTests.points(active, player));
        } catch (RuntimeException | AssertionError e) {
            GameManager.end(active);
            throw e;
        }
        int ready = settings.processTicks() + 1;
        helper.runAfterDelay(ready, () -> step(active, () -> {
            FeatureBlock.use(player, at);
            ItemStack sword = player.getMainHandItem();
            helper.assertTrue(sword.is(Items.DIAMOND_SWORD), "expected a diamond sword, have " + sword);
            helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, sword) == 3, "no Sharpness III: " + sword.getTag());
            helper.assertTrue(sword.getHoverName().getContents() instanceof TranslatableContents name && name.getKey().equals("item.packapunch.punch.iron_sword"),
                    "not named by the def: " + sword.getHoverName());

            player.setItemInHand(InteractionHand.MAIN_HAND, punchable(new ItemStack(Items.IRON_SWORD), "packapunch:iron_sword"));
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the second sword wasn't taken");
        }));
        int lost = ready + settings.processTicks() + settings.collectTicks() + 2;
        helper.runAfterDelay(lost, () -> step(active, () -> {
            helper.assertTrue(entity(helper, at).empty(), "an uncollected result should be lost after collect_ticks");
            FeatureBlock.use(player, at);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "a lost result can't be collected");
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 5000, "lost results aren't refunded");
        }));
        helper.runAfterDelay(lost + 1, () -> {
            GameManager.end(active);
            helper.succeed();
        });
    }
}
