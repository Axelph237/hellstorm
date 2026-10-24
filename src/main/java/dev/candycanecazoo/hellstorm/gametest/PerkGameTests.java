package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameHooks;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.perk.PerkEffects;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tacz.PerkGunEffects;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Perks as potions: bought from a vending machine, drunk for an effect with no time limit, and lost on going down. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerkGameTests {
    private static final ResourceLocation AK47 = ResourceLocation.tryBuild("tacz", "ak47");
    private static final float BASE_HEALTH = 20;

    private PerkGameTests() {}

    /** A vending machine selling a potion of {@code perk} at its default cost. */
    private static BlockPos machine(GameTestHelper helper, ResourceLocation perk) {
        BlockPos at = vendingMachine(helper);
        WallBuyBlock.entity(helper.getLevel(), at).orElseThrow().setConfig(VendingMachineBlock.sells(perk, VendingMachineBlock.DEFAULT_COST));
        return at;
    }

    /** A whole vending machine, its main block at (2, 2) and its front to the north. */
    static BlockPos vendingMachine(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        VendingMachineBlock.place(helper.getLevel(), at, Direction.NORTH);
        return at;
    }

    /** Buys from the vending machine at {@code at} without waiting out its vend. */
    static void buy(ServerPlayer player, BlockPos at) {
        FeatureBlock.use(player, at);
        if (player.level().getBlockEntity(at) instanceof VendingMachineBlock.Entity machine) {
            machine.handOver(player.serverLevel());
        }
    }

    /** Takes the first potion out of the player's inventory, the way a machine handed it over. */
    static ItemStack takePotion(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.POTION)) {
                return player.getInventory().removeItemNoUpdate(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Drinks {@code potion} as a player does: starting to drink can be refused, and then it's finished at once. */
    static boolean drink(ServerPlayer player, ItemStack potion) {
        player.setItemInHand(InteractionHand.MAIN_HAND, potion);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (!player.isUsingItem()) {
            return false;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, potion.finishUsingItem(player.level(), player));
        player.stopUsingItem();
        return true;
    }

    private static ItemStack potion(MobEffect effect) {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), ForgeRegistries.POTIONS.getValue(ForgeRegistries.MOB_EFFECTS.getKey(effect)));
    }

    private static void give(ServerPlayer player, MobEffect effect) {
        player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION));
    }

    @GameTest(template = "floor", batch = "perk_power", timeoutTicks = 200)
    public static void aVendingMachineSellsItsPotionForItsCostOnlyWithThePowerAndHandsItOverAfterItsVend(GameTestHelper helper) {
        BlockPos at = vendingMachine(helper);
        BlockPos lever = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(lever, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_power");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at, lever);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        // However the test ends, the game mustn't outlive it.
        helper.runAfterDelay(190, () -> GameManager.get(helper.getLevel()).filter(a -> a == active).ifPresent(GameManager::end));
        active.game().refund(player.getUUID(), 10_000);
        int start = PurchaseGameTests.points(active, player);
        FeatureBlock.use(player, at);
        helper.assertTrue(takePotion(player).isEmpty(), "a new vending machine sold its Vigoraid without power");
        helper.assertTrue(PurchaseGameTests.points(active, player) == start, "charged without power");

        active.game().turnPowerOn(Optional.empty());
        BlockPos upperHead = at.above().east();
        FeatureBlock.use(player, upperHead);
        int paid = start - VendingMachineBlock.DEFAULT_COST;
        helper.assertTrue(PurchaseGameTests.points(active, player) == paid,
                "using any block of it should charge " + VendingMachineBlock.DEFAULT_COST + ", have " + PurchaseGameTests.points(active, player));
        helper.assertTrue(takePotion(player).isEmpty(), "handed the potion over before the vend");
        FeatureBlock.use(player, at);
        helper.assertTrue(PurchaseGameTests.points(active, player) == paid, "a busy machine charged again");
        helper.runAfterDelay(VendingMachineBlock.VEND_TICKS - 2, () -> {
            try {
                helper.assertTrue(takePotion(player).isEmpty(), "handed the potion over before the vend ended");
                helper.assertTrue(events.stream().noneMatch(GameEvent.PerkBought.class::isInstance), "perk_bought before the potion was handed over");
            } catch (RuntimeException e) {
                GameManager.end(active);
                throw e;
            }
        });
        helper.runAfterDelay(VendingMachineBlock.VEND_TICKS + 2, () -> {
            try {
                ItemStack bought = takePotion(player);
                helper.assertTrue(PotionUtils.getMobEffects(bought).stream().anyMatch(e -> e.getEffect() == PerkEffects.VIGORADE.get()),
                        "expected a Vigoraid potion after the vend, got " + bought + " " + bought.getTag());
                helper.assertTrue(PurchaseGameTests.points(active, player) == paid, "paid " + (start - PurchaseGameTests.points(active, player)));
                helper.assertTrue(events.contains(new GameEvent.PerkBought(player.getUUID(), "hellstorm:vigorade", VendingMachineBlock.DEFAULT_COST)),
                        "no perk_bought event: " + events);
                helper.assertTrue(events.stream().noneMatch(GameEvent.WallBuy.class::isInstance), "a vending machine shouldn't report a wall_buy");
            } finally {
                GameManager.end(active);
            }
            helper.succeed();
        });
    }

    /** Placed like the crate, two blocks tall: needing room above, holding its block entity in the lower foot, and gone all at once. */
    @GameTest(template = "floor", batch = "perk_place")
    public static void aVendingMachineIsPlacedTwoTallFacingYouAndBreaksAsOne(GameTestHelper helper) {
        ServerPlayer player = PurchaseGameTests.player(helper, "perk_place");
        // Looking north, aiming at the left (west) half of the floor block.
        player.setYRot(180);
        BlockPos foot = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        placeOn(player, foot.below(), -0.25);
        List<BlockPos> blocks = List.of(foot, foot.west(), foot.above(), foot.west().above());
        List<String> parts = List.of("FOOT LOWER", "HEAD LOWER", "FOOT UPPER", "HEAD UPPER");
        for (int i = 0; i < blocks.size(); i++) {
            BlockState state = helper.getLevel().getBlockState(blocks.get(i));
            helper.assertTrue(state.is(PapRegistries.VENDING_MACHINE.get()) && parts.get(i).equals(state.getValue(VendingMachineBlock.PART).name()
                            + " " + state.getValue(VendingMachineBlock.HALF).name()) && state.getValue(VendingMachineBlock.FACING) == Direction.SOUTH,
                    "expected " + parts.get(i) + " facing south, got " + state);
        }
        helper.assertTrue(helper.getLevel().getBlockEntity(foot) instanceof VendingMachineBlock.Entity
                && blocks.stream().skip(1).noneMatch(p -> helper.getLevel().getBlockEntity(p) != null), "only the lower foot holds the block entity");

        BlockPos roofed = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        helper.getLevel().setBlockAndUpdate(roofed.above(), Blocks.STONE.defaultBlockState());
        placeOn(player, roofed.below(), -0.25);
        helper.assertTrue(helper.getLevel().getBlockState(roofed).isAir(), "placed with no room above");

        helper.getLevel().setBlockAndUpdate(foot.west().above(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(blocks.stream().allMatch(p -> helper.getLevel().getBlockState(p).isAir()), "breaking one block should take the whole machine");
        helper.succeed();
    }

    private static void placeOn(ServerPlayer player, BlockPos floor, double aimX) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PapRegistries.VENDING_MACHINE_ITEM.get()));
        Vec3 hit = Vec3.atCenterOf(floor).add(aimX, 0.5, 0);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, floor, false)));
    }

    @GameTest(template = "floor", batch = "perk_vigorade")
    public static void drinkingVigoradeGivesAnEndlessEffectThatRaisesMaxHealthAndHealsUntilTheGameEnds(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("vigorade"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_jug");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            buy(player, at);
            player.setHealth(10);
            helper.assertTrue(drink(player, takePotion(player)), "the bought Vigoraid couldn't be drunk");
            MobEffectInstance effect = player.getEffect(PerkEffects.VIGORADE.get());
            helper.assertTrue(effect != null && effect.isInfiniteDuration(), "Vigoraid should last forever, is " + effect);
            helper.assertTrue(player.getMaxHealth() == BASE_HEALTH + 20, "max health is " + player.getMaxHealth());
            helper.assertTrue(player.getHealth() == 30, "drinking should heal the 20 it adds, health " + player.getHealth());
        } finally {
            GameManager.end(active);
        }
        helper.assertFalse(player.hasEffect(PerkEffects.VIGORADE.get()), "Vigoraid outlived the game");
        helper.assertTrue(player.getMaxHealth() == BASE_HEALTH, "max health after the game is " + player.getMaxHealth());
        helper.assertTrue(player.getHealth() <= BASE_HEALTH, "health above the restored max");
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_downs")
    public static void goingDownOrDyingTakesEveryBuffButNotHarm(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("vigorade"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_downs");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            give(player, PerkEffects.VIGORADE.get());
            give(player, PerkEffects.OFF_HAND_PERK.get());
            give(player, MobEffects.DAMAGE_RESISTANCE);
            give(player, MobEffects.WEAKNESS);
            active.world().down(player.getUUID());
            helper.assertTrue(player.getActiveEffects().stream().noneMatch(e -> e.getEffect().isBeneficial()),
                    "going down should take every buff, has " + player.getActiveEffects());
            helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "going down shouldn't take a harmful effect");
            helper.assertTrue(player.getMaxHealth() == BASE_HEALTH, "max health after going down is " + player.getMaxHealth());
            helper.assertTrue(player.getAttributeValue(PapRegistries.EXTRA_WEAPON_SLOTS) == 0, "Off-Hand's slot outlived going down");

            give(player, PerkEffects.JOCKEY_ENERGY.get());
            GameHooks.onDeath(new LivingDeathEvent(player, player.damageSources().generic()));
            helper.assertFalse(player.hasEffect(PerkEffects.JOCKEY_ENERGY.get()), "dying in a game should take every buff too");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_limit")
    public static void atThePerkLimitANewPerkCanBeBoughtButNotDrunk(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("jockey_energy"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_limit");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            List<MobEffect> held = List.of(PerkEffects.VIGORADE.get(), PerkEffects.RELOAD.get(), PerkEffects.HELLFIRE.get(),
                    PerkEffects.SAINTS_BREW.get(), PerkEffects.OFF_HAND_PERK.get());
            int limit = active.game().settings().players().perkLimit();
            held.subList(0, limit).forEach(effect -> give(player, effect));

            buy(player, at);
            ItemStack jockeyEnergy = takePotion(player);
            helper.assertFalse(jockeyEnergy.isEmpty(), "at the limit the machine still sells a perk you don't have");
            helper.assertFalse(drink(player, jockeyEnergy), "drinking a perk past the limit should be refused");
            helper.assertFalse(player.hasEffect(PerkEffects.JOCKEY_ENERGY.get()), "Wandering Spirit was added past the limit");
            helper.assertTrue(player.getMainHandItem().is(Items.POTION), "the refused potion should not be used up");

            helper.assertTrue(drink(player, potion(PerkEffects.VIGORADE.get())), "drinking a perk already held isn't a new perk");
            helper.assertTrue(drink(player, PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.SWIFTNESS)),
                    "potions that aren't perks don't count");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(PerkEffects.JOCKEY_ENERGY.get(), net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION));
            helper.assertTrue(player.hasEffect(PerkEffects.JOCKEY_ENERGY.get()), "only drinking is limited: a lingering cloud or any other source can go past it");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_already_have")
    public static void aMachineRefusesAPotionWhoseEffectsThePlayerAlreadyHas(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("vigorade"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_have");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            give(player, PerkEffects.VIGORADE.get());
            WallBuyBlock.Entity entity = WallBuyBlock.entity(helper.getLevel(), at).orElseThrow();
            WallBuy.Action action = WallBuyBlock.action(entity.config(), entity.display(), player, Optional.empty());
            helper.assertTrue(action.equals(new WallBuy.Action.Deny("You already have Vigoraid")), "the prompt should hide, action " + action);

            int before = PurchaseGameTests.points(active, player);
            buy(player, at);
            helper.assertTrue(takePotion(player).isEmpty(), "sold a Vigoraid to a player who has it");
            helper.assertTrue(PurchaseGameTests.points(active, player) == before, "charged for a refused Vigoraid");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_can")
    public static void perkCansAreSoldAndDrunkLikePotionsForTheirPerkWithNoTimeLimit(GameTestHelper helper) {
        BlockPos at = vendingMachine(helper);
        WallBuyBlock.entity(helper.getLevel(), at).orElseThrow().setConfig(new WallBuy.Config(
                new dev.candycanecazoo.hellstorm.data.ItemSpec.Plain(Hellstorm.id("load_up"), 1, Optional.empty()),
                VendingMachineBlock.DEFAULT_COST, WallBuy.Repurchase.DENY, 0, WallBuy.DEFAULT_PAP_AMMO_COST, true));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_can");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            active.game().refund(player.getUUID(), 10_000);
            buy(player, at);
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(PapRegistries.LOAD_UP_CAN.get()));
            helper.assertTrue(slot >= 0, "the machine should hand over a Load-Up can");
            ItemStack can = player.getInventory().removeItemNoUpdate(slot);
            helper.assertTrue(can.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK && can.getUseDuration() == 32, "a can is drunk like a potion");
            helper.assertTrue(drink(player, can), "drinking the can shouldn't be refused");
            MobEffectInstance loadUp = player.getEffect(PerkEffects.RELOAD.get());
            helper.assertTrue(loadUp != null && loadUp.isInfiniteDuration(), "the can should give Load-Up with no time limit, got " + loadUp);
            helper.assertTrue(!loadUp.isVisible() && loadUp.showIcon(), "the can's Load-Up should give off no particles but keep its icon, got " + loadUp);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "nothing should be left of the can, hand holds " + player.getMainHandItem());

            WallBuyBlock.Entity entity = WallBuyBlock.entity(helper.getLevel(), at).orElseThrow();
            WallBuy.Action action = WallBuyBlock.action(entity.config(), entity.display(), player, Optional.empty());
            helper.assertTrue(action.equals(new WallBuy.Action.Deny("You already have Load-Up")), "a second can should be refused, action " + action);

            helper.assertTrue(drink(player, new ItemStack(PapRegistries.VIGORAID_CAN.get())), "drinking a Vigoraid can shouldn't be refused");
            MobEffectInstance vigoraid = player.getEffect(PerkEffects.VIGORADE.get());
            helper.assertTrue(vigoraid != null && vigoraid.isInfiniteDuration(), "the Vigoraid can should give Vigoraid with no time limit, got " + vigoraid);
            helper.assertTrue(drink(player, new ItemStack(PapRegistries.SAINTS_BREW_CAN.get())), "drinking a Saint's Brew can shouldn't be refused");
            MobEffectInstance saintsBrew = player.getEffect(PerkEffects.SAINTS_BREW.get());
            helper.assertTrue(saintsBrew != null && saintsBrew.isInfiniteDuration(), "the Saint's Brew can should give Saint's Brew with no time limit, got " + saintsBrew);
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "perk_self_revive")
    public static void saintsBrewGivesSoloSelfReviveAndRevivesFaster(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("saints_brew"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_revive");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            helper.assertFalse(active.world().canSelfRevive(player.getUUID()), "self-revive without Saint's Brew");
            give(player, PerkEffects.SAINTS_BREW.get());
            helper.assertTrue(active.world().canSelfRevive(player.getUUID()), "Saint's Brew is in hellstorm:self_revive");
            helper.assertTrue(active.world().reviveSpeed(player.getUUID()) == 2, "revive speed is " + active.world().reviveSpeed(player.getUUID()));
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor")
    public static void aMachineSavedWithAPerkLoadsAsThatPerksPotion(GameTestHelper helper) {
        BlockPos at = vendingMachine(helper);
        WallBuyBlock.Entity entity = WallBuyBlock.entity(helper.getLevel(), at).orElseThrow();
        helper.assertTrue(entity.config().equals(VendingMachineBlock.DEFAULT), "a new machine should sell Vigoraid, sells " + entity.config());

        entity.load(legacy("hellstorm:saints_brew", Optional.of(1500)));
        helper.assertTrue(entity.config().equals(VendingMachineBlock.sells(Hellstorm.id("saints_brew"), 1500)), "with its cost: " + entity.config());
        helper.assertTrue(entity.config().requiresPower() && entity.config().repurchase() == WallBuy.Repurchase.DENY, "old machines need power and sell once");
        entity.load(legacy("hellstorm:hellfire", Optional.empty()));
        helper.assertTrue(entity.config().equals(VendingMachineBlock.sells(Hellstorm.id("hellfire"), VendingMachineBlock.DEFAULT_COST)),
                "without a cost: " + entity.config());
        helper.assertTrue(entity.itemName().equals("Hellfire"), "the machine shows " + entity.itemName());
        entity.load(legacy("nope:perk", Optional.empty()));
        helper.assertTrue(entity.config().equals(VendingMachineBlock.sells(Hellstorm.id("hellfire"), VendingMachineBlock.DEFAULT_COST)),
                "an unknown old perk keeps what was there: " + entity.config());
        helper.succeed();
    }

    private static CompoundTag legacy(String perk, Optional<Integer> cost) {
        CompoundTag config = new CompoundTag();
        config.putString("perk", perk);
        cost.ifPresent(c -> config.putInt("cost", c));
        CompoundTag tag = new CompoundTag();
        tag.put("config", config);
        return tag;
    }

    @GameTest(template = "floor", batch = "perk_hellfire")
    public static void hellfireRaisesTheHeldGunsFireRate(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("hellfire"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_tap");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        int base;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, TaczBridge.createGun(AK47, 1)
                    .orElseThrow(() -> new IllegalStateException("tacz:ak47 missing")));
            PerkGunEffects.refresh(player);
            base = rpm(player);
            helper.assertTrue(base > 0, "no cached rpm for the AK");
            give(player, PerkEffects.HELLFIRE.get());
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
    @GameTest(template = "floor", batch = "perk_reload")
    public static void reLoadFinishesFeedingAReloadInHalfTheTime(GameTestHelper helper) {
        BlockPos at = machine(helper, Hellstorm.id("reload"));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_perk_cola");
        GameManager.Active active = PurchaseGameTests.game(helper, player, at);
        try {
            long feed = (long) (TimelessAPI.getCommonGunIndex(AK47).orElseThrow().getGunData().getReloadData().getFeed().getEmptyTime() * 1000);
            long elapsed = feed * 3 / 4;
            Reloaded before = reloadAfter(player, elapsed);
            helper.assertTrue(before.state() == ReloadState.StateType.EMPTY_RELOAD_FEEDING && before.magazine() == 0,
                    "without Load-Up, " + elapsed + " of " + feed + " ms in should still be feeding, was " + before);

            AttributeInstance reloadSpeed = player.getAttribute(PapRegistries.RELOAD_SPEED);
            helper.assertTrue(reloadSpeed != null && reloadSpeed.getValue() == 1, "players should start at reload speed 1");

            active.game().refund(player.getUUID(), 10_000);
            buy(player, at);
            helper.assertTrue(drink(player, takePotion(player)), "Load-Up wasn't bought");
            helper.assertTrue(reloadSpeed.getValue() == 2, "Load-Up should add 1 to reload speed, is " + reloadSpeed.getValue());
            Reloaded after = reloadAfter(player, elapsed);
            helper.assertTrue(after.state() != ReloadState.StateType.EMPTY_RELOAD_FEEDING && after.magazine() > 0,
                    "with Load-Up, " + elapsed + " of " + feed + " ms in should have fed the magazine, was " + after);
        } finally {
            GameManager.end(active);
        }
        AttributeInstance reloadSpeed = player.getAttribute(PapRegistries.RELOAD_SPEED);
        helper.assertTrue(reloadSpeed.getValue() == 1, "reload speed after the game is " + reloadSpeed.getValue());
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "reload_attribute")
    public static void anyReloadSpeedModifierSpeedsUpReloadsWithoutAPerk(GameTestHelper helper) {
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_reload_attribute");
        long feed = (long) (TimelessAPI.getCommonGunIndex(AK47).orElseThrow().getGunData().getReloadData().getFeed().getEmptyTime() * 1000);
        AttributeInstance reloadSpeed = player.getAttribute(PapRegistries.RELOAD_SPEED);
        AttributeModifier boost = new AttributeModifier(java.util.UUID.randomUUID(), "test boost", 1.0, AttributeModifier.Operation.MULTIPLY_BASE);
        reloadSpeed.addTransientModifier(boost);
        try {
            Reloaded reloaded = reloadAfter(player, feed * 3 / 4);
            helper.assertTrue(reloaded.magazine() > 0, "at reload speed " + reloadSpeed.getValue() + " the magazine should be fed, was " + reloaded);
        } finally {
            reloadSpeed.removeModifier(boost);
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
        // From now, not from when reload() ran, so a slow tick between the two can't add to it.
        data.reloadTimestamp = System.currentTimeMillis() - elapsedMillis;
        ReloadState state = ((AbstractGunItem) gun.getItem()).tickReload(data, gun, player);
        return new Reloaded(state.getStateType(), iGun.getCurrentAmmoCount(gun));
    }

    private static int rpm(ServerPlayer player) {
        Integer rpm = IGunOperator.fromLivingEntity(player).getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE);
        return rpm == null ? -1 : rpm;
    }
}
