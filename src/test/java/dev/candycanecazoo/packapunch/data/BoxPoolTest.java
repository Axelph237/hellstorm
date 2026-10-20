package dev.candycanecazoo.packapunch.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.packapunch.feature.MysteryBoxes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BoxPoolTest {
    private static ResourceLocation tacz(String id) {
        return ResourceLocation.tryBuild("tacz", id);
    }

    private static ItemSpec gun(String id) {
        return ItemSpec.Gun.of(tacz(id));
    }

    private static final List<ResourceLocation> GUNS = List.of(tacz("ak47"), tacz("glock_17"), tacz("m249"), tacz("m4a1"));
    private static final ItemSpec TOTEM = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "totem_of_undying"), 1, Optional.empty());

    private static BoxPool parse(String json) {
        return BoxPool.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, e -> {});
    }

    @Test
    void allGunsMinusWallBuysPlusEntriesThenOverrides() {
        BoxPool pool = parse("""
                { "include_all_tacz_guns": true, "exclude_wall_buys": true,
                  "entries": [ { "item": { "tacz_gun": "tacz:m249" }, "weight": 2 },
                               { "item": { "item": "minecraft:totem_of_undying" }, "weight": 1 } ],
                  "overrides": { "tacz:glock_17": { "weight": 0 } } }""");
        List<BoxPool.Weighted> built = pool.build(GUNS, List.of(new ItemSpec.Gun(tacz("m4a1"), true, false)));
        assertEquals(List.of(
                new BoxPool.Weighted(gun("ak47"), 1),
                new BoxPool.Weighted(gun("m249"), 2),
                new BoxPool.Weighted(TOTEM, 1)), built);
    }

    @Test
    void withoutTheFlagsOnlyEntriesCount() {
        BoxPool pool = parse("{ \"entries\": [ { \"item\": { \"tacz_gun\": \"tacz:ak47\" } } ], \"overrides\": { \"tacz:ak47\": { \"weight\": 5 } } }");
        assertEquals(List.of(new BoxPool.Weighted(gun("ak47"), 5)), pool.build(GUNS, List.of(gun("ak47"))));
    }

    @Test
    void theShippedDefaultPoolIsEveryGunExceptWallBuys() throws IOException {
        BoxPool pool = parse(Files.readString(Path.of("src/main/resources/data/packapunch/packapunch/box_pools/default.json")));
        assertEquals(BoxPool.DEFAULT, pool);
        assertEquals(List.of(gun("ak47"), gun("glock_17"), gun("m4a1")),
                pool.build(GUNS, List.of(gun("m249"))).stream().map(BoxPool.Weighted::item).toList());
    }

    @Test
    void badValuesAreErrors() {
        assertTrue(BoxPool.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"include_all_tacz_guns\": \"yes\"}")).error().isPresent());
        assertTrue(BoxPool.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entries\": [ { \"item\": { \"tacz_gun\": \"tacz:ak47\" }, \"weight\": -1 } ]}")).error().isPresent());
    }

    @Test
    void tenThousandRollsMatchTheWeights() {
        List<BoxPool.Weighted> pool = List.of(new BoxPool.Weighted(gun("ak47"), 1), new BoxPool.Weighted(gun("m249"), 3), new BoxPool.Weighted(TOTEM, 6));
        RandomSource random = RandomSource.create(42);
        Map<ItemSpec, Integer> counts = new HashMap<>();
        int rolls = 10_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(BoxPool.pick(pool, spec -> false, random).orElseThrow(), 1, Integer::sum);
        }
        for (BoxPool.Weighted w : pool) {
            double expected = rolls * w.weight() / 10.0;
            assertEquals(expected, counts.get(w.item()), expected * 0.06, w.item().describe());
        }
    }

    @Test
    void picksSkipWhatThePlayerHas() {
        List<BoxPool.Weighted> pool = List.of(new BoxPool.Weighted(gun("ak47"), 100), new BoxPool.Weighted(gun("m249"), 1));
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 100; i++) {
            assertEquals(Optional.of(gun("m249")), BoxPool.pick(pool, spec -> spec.equals(gun("ak47")), random));
        }
        assertEquals(Optional.empty(), BoxPool.pick(pool, spec -> true, random));
    }

    @Test
    void theBoxMovesOnlyAfterTheMinimumUses() {
        assertFalse(MysteryBoxes.shouldMove(3, 4, 0.15, 0.0));
        assertTrue(MysteryBoxes.shouldMove(4, 4, 0.15, 0.1));
        assertFalse(MysteryBoxes.shouldMove(10, 4, 0.15, 0.2));
    }
}
