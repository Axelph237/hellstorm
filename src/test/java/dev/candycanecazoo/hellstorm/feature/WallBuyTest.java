package dev.candycanecazoo.hellstorm.feature;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WallBuyTest {
    private static final ItemSpec AK = ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47"));
    private static final ItemSpec APPLES = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3, Optional.empty());
    private static final ItemSpec SWORD = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "iron_sword"), 1, Optional.empty());

    private static WallBuy.Config gun(WallBuy.Repurchase repurchase) {
        return new WallBuy.Config(AK, 1200, repurchase, 600, 4500, false);
    }

    @Test
    void notOwnedIsAPurchaseAtTheFullCost() {
        for (WallBuy.Repurchase mode : WallBuy.Repurchase.values()) {
            assertEquals(new WallBuy.Action.Buy(1200), WallBuy.decide(gun(mode), Optional.empty()), mode.name());
        }
    }

    @Test
    void ownedGunsRefillAtTheAmmoCostOrThePapAmmoCost() {
        assertEquals(new WallBuy.Action.Refill(600), WallBuy.decide(gun(WallBuy.Repurchase.REFILL_AMMO), Optional.of(new WallBuy.Owned(1, false))));
        assertEquals(new WallBuy.Action.Refill(4500), WallBuy.decide(gun(WallBuy.Repurchase.REFILL_AMMO), Optional.of(new WallBuy.Owned(1, true))));
    }

    @Test
    void topUpFillsBackToTheSpecCountAndRefusesWhenFull() {
        WallBuy.Config apples = new WallBuy.Config(APPLES, 300, WallBuy.Repurchase.TOP_UP, 150, 4500, false);
        assertEquals(new WallBuy.Action.TopUp(300, 2), WallBuy.decide(apples, Optional.of(new WallBuy.Owned(1, false))));
        assertInstanceOf(WallBuy.Action.Deny.class, WallBuy.decide(apples, Optional.of(new WallBuy.Owned(3, false))));
        assertInstanceOf(WallBuy.Action.Deny.class, WallBuy.decide(apples, Optional.of(new WallBuy.Owned(5, false))));
    }

    @Test
    void duplicateBuysAnotherAndDenyRefusesWithoutCost() {
        assertEquals(new WallBuy.Action.Buy(1200), WallBuy.decide(gun(WallBuy.Repurchase.DUPLICATE), Optional.of(new WallBuy.Owned(1, false))));
        WallBuy.Action deny = WallBuy.decide(gun(WallBuy.Repurchase.DENY), Optional.of(new WallBuy.Owned(1, false)));
        assertInstanceOf(WallBuy.Action.Deny.class, deny);
        assertEquals(0, deny.cost());
    }

    @Test
    void refillAmmoOnAPlainItemTopsUpInstead() {
        WallBuy.Config apples = new WallBuy.Config(APPLES, 300, WallBuy.Repurchase.REFILL_AMMO, 150, 4500, false);
        assertEquals(new WallBuy.Action.TopUp(300, 1), WallBuy.decide(apples, Optional.of(new WallBuy.Owned(2, false))));
    }

    @Test
    void defaultRepurchaseFollowsTheItemKind() {
        assertEquals(WallBuy.Repurchase.REFILL_AMMO, WallBuy.Repurchase.defaultFor(AK, false));
        assertEquals(WallBuy.Repurchase.TOP_UP, WallBuy.Repurchase.defaultFor(APPLES, true));
        assertEquals(WallBuy.Repurchase.DENY, WallBuy.Repurchase.defaultFor(SWORD, false));
    }

    @Test
    void codecDefaultsAmmoToHalfTheCostAndRoundTrips() {
        WallBuy.Config parsed = WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\": {\"tacz_gun\": \"tacz:ak47\"}, \"cost\": 1000}")).getOrThrow(false, e -> {});
        assertEquals(new WallBuy.Config(AK, 1000, WallBuy.Repurchase.REFILL_AMMO, 500, WallBuy.DEFAULT_PAP_AMMO_COST, false), parsed);
        WallBuy.Config apples = new WallBuy.Config(APPLES, 300, WallBuy.Repurchase.DUPLICATE, 10, 20, false);
        assertEquals(apples, WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                WallBuy.Config.CODEC.encodeStart(JsonOps.INSTANCE, apples).getOrThrow(false, e -> {})).getOrThrow(false, e -> {}));
        assertTrue(WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\": {\"tacz_gun\": \"tacz:ak47\"}, \"cost\": 1000, \"repurchase\": \"refil\"}")).error().isPresent());
    }

    @Test
    void requiresPowerIsOffUnlessSetAndRoundTrips() {
        WallBuy.Config powered = WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\": {\"tacz_gun\": \"tacz:ak47\"}, \"cost\": 1000, \"requires_power\": true}")).getOrThrow(false, e -> {});
        assertTrue(powered.requiresPower());
        assertEquals(powered, WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                WallBuy.Config.CODEC.encodeStart(JsonOps.INSTANCE, powered).getOrThrow(false, e -> {})).getOrThrow(false, e -> {}));
        assertFalse(WallBuy.Config.forItem(APPLES, 300, WallBuy.Repurchase.TOP_UP).requiresPower());
        assertTrue(WallBuy.Config.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\": {\"tacz_gun\": \"tacz:ak47\"}, \"cost\": 1000, \"requires_power\": \"yes\"}")).error().isPresent());
    }
}
