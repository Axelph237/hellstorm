package dev.candycanecazoo.hellstorm.config;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseDefaultsTest {
    /** TacZ 1.1.8's default gun pack, from its {@code data/tacz/index/guns}. */
    private static final List<String> DEFAULT_PACK = List.of("aa12", "ai_awp", "ak47", "aug", "b93r", "cz75", "db_long", "db_short",
            "deagle", "deagle_golden", "fn_evolys", "fn_fal", "g36k", "glock_17", "hk416d", "hk_g3", "hk_mk23", "hk_mp5a5", "kar98",
            "lonetrail", "m1014", "m107", "m16a1", "m16a4", "m1911", "m249", "m320", "m4a1", "m700", "m870", "m95", "m9a4", "minigun",
            "mk14", "p320", "p90", "qbz_191", "qbz_95", "rhino357", "rpg7", "rpk", "scar_h", "scar_l", "sks_tactical", "spas_12",
            "spr15hb", "springfield1873", "taurus500", "taurus943", "timeless50", "type_81", "ump45", "uzi", "vector45");

    @Test
    void theBuiltInListsReadCleanlyAndCoverEveryDefaultGun() {
        PurchaseDefaults.Parsed parsed = PurchaseDefaults.parse(PurchaseDefaults.DEFAULT_GUNS, PurchaseDefaults.DEFAULT_ITEMS);
        assertEquals(List.of(), parsed.problems());
        for (String gun : DEFAULT_PACK) {
            Optional<PurchaseDefaults.Purchase> listed = parsed.defaults().of(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", gun)));
            assertTrue(listed.isPresent(), gun + " should be listed");
            assertEquals(WallBuy.Repurchase.REFILL_AMMO, listed.get().repurchase(), gun + " should refill ammo");
        }
        assertEquals(DEFAULT_PACK.size(), parsed.defaults().guns().size());
    }

    @Test
    void theStartingWallBuyGunCostsWhatAPlacedWallBuyAlwaysHas() {
        assertEquals(Optional.of(WallBuy.Config.DEFAULT.cost()), PurchaseDefaults.BUILT_IN.of(WallBuy.Config.DEFAULT.item()).map(PurchaseDefaults.Purchase::cost));
    }

    @Test
    void anEntryCanLeaveOffItsAmmoCosts() {
        PurchaseDefaults defaults = PurchaseDefaults.parse(List.of("tacz:ak47 = 1000, refill_ammo"), List.of()).defaults();
        assertEquals(Optional.of(new PurchaseDefaults.Purchase(1000, WallBuy.Repurchase.REFILL_AMMO, 500, WallBuy.DEFAULT_PAP_AMMO_COST)),
                defaults.of(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47"))));
    }

    @Test
    void gunsAndItemsAreLookedUpInTheirOwnList() {
        PurchaseDefaults defaults = PurchaseDefaults.parse(List.of("tacz:ak47 = 1000, refill_ammo"), List.of("tacz:ak47 = 7, top_up")).defaults();
        assertEquals(1000, defaults.of(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47"))).orElseThrow().cost());
        assertEquals(7, defaults.of(new ItemSpec.Plain(ResourceLocation.tryBuild("tacz", "ak47"), 1, Optional.empty())).orElseThrow().cost());
        assertEquals(Optional.empty(), defaults.of(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "m4a1"))));
    }

    @Test
    void badEntriesAreLeftOutAndReported() {
        PurchaseDefaults.Parsed parsed = PurchaseDefaults.parse(
                List.of("tacz:ak47 1200, refill_ammo", "tacz:m4a1 = lots, refill_ammo", "tacz:aug = 1500, reload", "tacz:uzi = 900, refill_ammo, 400",
                        "tacz:p90 = 1300, refill_ammo, -1, 4500", "tacz:mk14 = 1500, refill_ammo", "tacz:mk14 = 1600, refill_ammo"),
                List.of("minecraft:golden_apple = 500, refill_ammo"));
        assertEquals(7, parsed.problems().size(), parsed.problems().toString());
        assertEquals(1, parsed.defaults().guns().size());
        assertEquals(1600, parsed.defaults().guns().get(ResourceLocation.tryBuild("tacz", "mk14")).cost());
        assertTrue(parsed.defaults().items().isEmpty());
    }
}
