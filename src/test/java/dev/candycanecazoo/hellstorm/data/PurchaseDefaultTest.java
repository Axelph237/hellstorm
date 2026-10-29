package dev.candycanecazoo.hellstorm.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseDefaultTest {
    /** TacZ 1.1.8's default gun pack, from its {@code data/tacz/index/guns}. */
    private static final List<String> DEFAULT_PACK = List.of("aa12", "ai_awp", "ak47", "aug", "b93r", "cz75", "db_long", "db_short",
            "deagle", "deagle_golden", "fn_evolys", "fn_fal", "g36k", "glock_17", "hk416d", "hk_g3", "hk_mk23", "hk_mp5a5", "kar98",
            "lonetrail", "m1014", "m107", "m16a1", "m16a4", "m1911", "m249", "m320", "m4a1", "m700", "m870", "m95", "m9a4", "minigun",
            "mk14", "p320", "p90", "qbz_191", "qbz_95", "rhino357", "rpg7", "rpk", "scar_h", "scar_l", "sks_tactical", "spas_12",
            "spr15hb", "springfield1873", "taurus500", "taurus943", "timeless50", "type_81", "ump45", "uzi", "vector45");
    private static final Path GUNS = Path.of("src/main/resources/data/tacz/hellstorm/gun_purchases");
    private static final Path ITEMS = Path.of("src/main/resources/data/hellstorm/hellstorm/item_purchases");

    private static PurchaseDefault parse(String json) {
        return PurchaseDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, AssertionError::new);
    }

    @Test
    void leftOutAmmoCostsAreHalfThePriceAndTheWallBuyDefault() {
        assertEquals(new PurchaseDefault(1000, WallBuy.Repurchase.REFILL_AMMO, 500, WallBuy.DEFAULT_PAP_AMMO_COST),
                parse("{\"cost\": 1000, \"repurchase\": \"refill_ammo\"}"));
    }

    @Test
    void aFileNeedsACostAndABuyingAgainMode() {
        assertTrue(PurchaseDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cost\": 1000}")).error().isPresent());
        assertTrue(PurchaseDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cost\": -1, \"repurchase\": \"deny\"}")).error().isPresent());
        assertTrue(PurchaseDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cost\": 1, \"repurchase\": \"reload\"}")).error().isPresent());
    }

    @Test
    void onlyGunsRefillAmmo() {
        assertTrue(PurchaseDefaults.itemProblem(parse("{\"cost\": 500, \"repurchase\": \"refill_ammo\"}")).isPresent());
        assertEquals(Optional.empty(), PurchaseDefaults.itemProblem(parse("{\"cost\": 500, \"repurchase\": \"top_up\"}")));
    }

    @Test
    void everyDefaultGunHasAFileThatRefillsAmmo() throws IOException {
        for (String gun : DEFAULT_PACK) {
            PurchaseDefault purchase = parse(Files.readString(GUNS.resolve(gun + ".json")));
            assertEquals(WallBuy.Repurchase.REFILL_AMMO, purchase.repurchase(), gun + " should refill ammo");
        }
        try (var files = Files.list(GUNS)) {
            assertEquals(DEFAULT_PACK.size(), files.count());
        }
    }

    @Test
    void theStartingWallBuyGunCostsWhatAPlacedWallBuyAlwaysHas() throws IOException {
        assertEquals(WallBuy.Config.DEFAULT.cost(), parse(Files.readString(GUNS.resolve("ak47.json"))).cost());
    }

    @Test
    void everyPerkCanHasAFileThatRefusesASecondCan() throws IOException {
        for (String can : List.of("vigoraid", "load_up", "saints_brew", "off_hand", "jockey_energy")) {
            PurchaseDefault purchase = parse(Files.readString(ITEMS.resolve(can + ".json")));
            assertEquals(WallBuy.Repurchase.DENY, purchase.repurchase(), can);
            assertEquals(Optional.empty(), PurchaseDefaults.itemProblem(purchase), can);
        }
    }
}
