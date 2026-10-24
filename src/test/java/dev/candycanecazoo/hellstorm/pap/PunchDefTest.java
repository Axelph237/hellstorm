package dev.candycanecazoo.hellstorm.pap;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PunchDefTest {
    private static final ResourceLocation AK = ResourceLocation.tryBuild("tacz", "ak47");

    private static PunchDef parse(String json) {
        return PunchDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, e -> {});
    }

    private static boolean invalid(String json) {
        return PunchDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent();
    }

    private static PunchDefs.File file(String id, String json) {
        return new PunchDefs.File(ResourceLocation.tryParse(id), JsonParser.parseString(json));
    }

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseTag(text);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void readsEveryField() {
        PunchDef def = parse("""
                { "item-id": "tacz:m4a1", "nbt": { "pap": { "punch_id": "mymap:m4_tier3" } }, "name": "item.mymap.m4_upgraded",
                  "cost": 4000, "carry-over": { "ammo": false, "attachments": true } }""");
        assertEquals(ResourceLocation.tryBuild("tacz", "m4a1"), def.itemId());
        assertEquals(Optional.of(snbt("{pap:{punch_id:\"mymap:m4_tier3\"}}")), def.nbt());
        assertEquals(Optional.of("item.mymap.m4_upgraded"), def.name());
        assertEquals(Optional.of(4000), def.cost());
        assertEquals(new PunchDef.CarryOver(false, true), def.carryOver());
    }

    @Test
    void onlyTheItemIdIsRequired() {
        PunchDef def = parse("{ \"item-id\": \"minecraft:diamond_sword\" }");
        assertEquals(Optional.empty(), def.nbt());
        assertEquals(Optional.empty(), def.name());
        assertEquals(Optional.empty(), def.cost());
        assertEquals(PunchDef.CarryOver.NONE, def.carryOver());
        assertEquals(new PunchDef.CarryOver(true, false), parse("{ \"item-id\": \"minecraft:stick\", \"carry-over\": { \"ammo\": true } }").carryOver());
    }

    @Test
    void nbtIsAnObjectOrAnSnbtString() {
        CompoundTag fromObject = parse("""
                { "item-id": "minecraft:diamond_sword",
                  "nbt": { "Enchantments": [ { "id": "minecraft:sharpness", "lvl": 3 } ], "Unbreakable": true, "Big": 70000, "Half": 0.5,
                           "display": { "Lore": [ "\\"Hot\\"" ] } } }""").nbt().orElseThrow();
        CompoundTag sharpness = fromObject.getList("Enchantments", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("minecraft:sharpness", sharpness.getString("id"));
        assertEquals(3, sharpness.getShort("lvl"));
        assertTrue(fromObject.getBoolean("Unbreakable"));
        assertEquals(70000, fromObject.getInt("Big"));
        assertEquals(0.5f, fromObject.getFloat("Half"));
        assertEquals("\"Hot\"", fromObject.getCompound("display").getList("Lore", net.minecraft.nbt.Tag.TAG_STRING).getString(0));

        CompoundTag fromString = parse("{ \"item-id\": \"minecraft:diamond_sword\", \"nbt\": \"{Enchantments:[{id:'minecraft:sharpness',lvl:3s}]}\" }")
                .nbt().orElseThrow();
        assertEquals(snbt("{Enchantments:[{id:\"minecraft:sharpness\",lvl:3s}]}"), fromString);
    }

    @Test
    void invalidValuesAreErrorsNotDefaults() {
        assertTrue(invalid("{}"), "no item-id");
        assertTrue(invalid("{ \"item_id\": \"minecraft:stick\" }"), "item_id is not item-id");
        assertTrue(invalid("{ \"item-id\": \"Not An Id!\" }"));
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"nbt\": \"{Damage:\" }"), "broken SNBT");
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"nbt\": [1, 2] }"), "nbt that isn't a compound");
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"cost\": -1 }"));
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"cost\": \"cheap\" }"));
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"name\": 7 }"));
        assertTrue(invalid("{ \"item-id\": \"minecraft:stick\", \"carry-over\": { \"ammo\": \"yes\" } }"));
    }

    @Test
    void roundTrips() {
        PunchDef def = parse("{ \"item-id\": \"tacz:ak47\", \"nbt\": { \"pap\": { \"tier\": 2 } }, \"cost\": 10, \"carry-over\": { \"attachments\": true } }");
        assertEquals(def, parse(PunchDef.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow(false, e -> {}).toString()));
    }

    @Test
    void aGunIdBuildsAPunchableGunWithItsNbtAndAnythingElseIsThePlainItem() {
        CompoundTag nbt = snbt("{pap:{punch_id:\"mymap:ak_tier3\"}}");
        assertEquals(new ItemSpec.Gun(AK, false, true, Optional.of(nbt)),
                new PunchDef(AK, Optional.of(nbt), Optional.empty(), Optional.empty(), PunchDef.CarryOver.NONE).result(AK::equals));
        CompoundTag notPunchable = snbt("{pap:{punchable:0b}}");
        assertEquals(new ItemSpec.Gun(AK, false, false, Optional.of(notPunchable)),
                new PunchDef(AK, Optional.of(notPunchable), Optional.empty(), Optional.empty(), PunchDef.CarryOver.NONE).result(AK::equals));
        ResourceLocation sword = ResourceLocation.tryBuild("minecraft", "diamond_sword");
        assertEquals(new ItemSpec.Plain(sword, 1, Optional.of(nbt)),
                new PunchDef(sword, Optional.of(nbt), Optional.empty(), Optional.empty(), PunchDef.CarryOver.NONE).result(AK::equals));
    }

    @Test
    void aFileHoldsManyEntriesNamespacedByTheFileUnlessTheKeyHasOne() {
        PunchDefs.Loaded loaded = PunchDefs.parse(List.of(file("mymap:upgrades", """
                { "sword_mk2": { "item-id": "minecraft:diamond_sword" },
                  "other:axe_mk2": { "item-id": "minecraft:diamond_axe" },
                  "mymap:defaults": { "item-id": "minecraft:stick" } }""")));
        assertEquals(Set.of(ResourceLocation.tryBuild("mymap", "sword_mk2"), ResourceLocation.tryBuild("other", "axe_mk2"),
                ResourceLocation.tryBuild("mymap", "defaults")), loaded.defs().keySet());
        assertEquals(List.of(), loaded.errors());
        assertEquals(Map.of(), loaded.defaults());
    }

    @Test
    void defaultsIsReservedForGunClasses() {
        PunchDefs.Loaded loaded = PunchDefs.parse(List.of(file("mymap:upgrades", """
                { "defaults": { "pistol": { "nbt": { "pap": { "damage_multiplier": 3.0 } }, "method": "merge" }, "rifle": {} } }""")));
        assertEquals(Map.of(), loaded.defs());
        assertEquals(Set.of("pistol", "rifle"), loaded.defaults().keySet());
        assertEquals(GunDefault.Method.MERGE, loaded.defaults().get("pistol").method());
        assertEquals(new GunDefault(new CompoundTag(), GunDefault.Method.MODIFY), loaded.defaults().get("rifle"));
    }

    @Test
    void laterFilesOverrideEarlierOnesEntryByEntry() {
        PunchDefs.Loaded loaded = PunchDefs.parse(List.of(
                file("hellstorm:iron_sword", "{ \"iron_sword\": { \"item-id\": \"minecraft:diamond_sword\", \"cost\": 2500 }, \"defaults\": { \"rifle\": {}, \"smg\": {} } }"),
                file("mymap:overrides", "{ \"hellstorm:iron_sword\": { \"item-id\": \"minecraft:netherite_sword\" }, \"defaults\": { \"rifle\": { \"method\": \"remove\" } } }")));
        assertEquals(ResourceLocation.tryBuild("minecraft", "netherite_sword"), loaded.defs().get(ResourceLocation.tryBuild("hellstorm", "iron_sword")).itemId());
        assertEquals(GunDefault.Method.REMOVE, loaded.defaults().get("rifle").method());
        assertEquals(GunDefault.Method.MODIFY, loaded.defaults().get("smg").method(), "a class the later file doesn't name is kept");
    }

    @Test
    void aBadEntryIsSkippedAndReportedAndTheRestOfItsFileKept() {
        PunchDefs.Loaded loaded = PunchDefs.parse(List.of(
                file("mymap:upgrades", """
                        { "good": { "item-id": "minecraft:stick" },
                          "bad": { "item-id": "minecraft:stick", "cost": -5 },
                          "Not An Id": { "item-id": "minecraft:stick" },
                          "defaults": { "pistol": { "method": "replace" }, "smg": { "method": "merge" } } }"""),
                file("mymap:broken", "[1, 2]"),
                file("mymap:bad_defaults", "{ \"defaults\": [\"pistol\"] }")));
        assertEquals(Set.of(ResourceLocation.tryBuild("mymap", "good")), loaded.defs().keySet());
        assertEquals(Set.of("smg"), loaded.defaults().keySet());
        assertEquals(5, loaded.errors().size(), String.join("\n", loaded.errors()));
        assertTrue(loaded.errors().stream().anyMatch(e -> e.startsWith("mymap:upgrades, \"bad\": cost")), String.join("\n", loaded.errors()));
        assertTrue(loaded.errors().stream().anyMatch(e -> e.startsWith("mymap:upgrades, \"Not An Id\": not a valid pap id")), String.join("\n", loaded.errors()));
        assertTrue(loaded.errors().stream().anyMatch(e -> e.startsWith("mymap:upgrades, defaults \"pistol\": method")
                && e.contains("Unknown method \"replace\": use merge, modify or remove")), String.join("\n", loaded.errors()));
        assertTrue(loaded.errors().contains("mymap:broken: not a JSON object"), String.join("\n", loaded.errors()));
        assertTrue(loaded.errors().contains("mymap:bad_defaults, \"defaults\": not an object of gun classes"), String.join("\n", loaded.errors()));
    }

    @Test
    void theShippedDefaultsLoadCleanly() throws java.io.IOException {
        java.nio.file.Path folder = java.nio.file.Path.of("src/main/resources/data/hellstorm/hellstorm/punch");
        List<PunchDefs.File> files;
        try (var paths = java.nio.file.Files.list(folder)) {
            files = paths.map(path -> {
                try {
                    String name = path.getFileName().toString();
                    return new PunchDefs.File(ResourceLocation.tryBuild("hellstorm", name.substring(0, name.length() - ".json".length())),
                            JsonParser.parseString(java.nio.file.Files.readString(path)));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
        }
        PunchDefs.Loaded loaded = PunchDefs.parse(files);
        assertEquals(List.of(), loaded.errors());
        PunchDef sword = loaded.defs().get(ResourceLocation.tryBuild("hellstorm", "iron_sword"));
        assertEquals(ResourceLocation.tryBuild("minecraft", "diamond_sword"), sword.itemId());
        assertEquals(Optional.of(2500), sword.cost());
    }
}
