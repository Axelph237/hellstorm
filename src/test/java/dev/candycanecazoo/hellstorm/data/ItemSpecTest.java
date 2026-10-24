package dev.candycanecazoo.hellstorm.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemSpecTest {
    private static ItemSpec parse(String json) {
        return ItemSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, e -> {});
    }

    @Test
    void theGunShorthandIsAPunchableGunByDefault() {
        assertEquals(new ItemSpec.Gun(new ResourceLocation("tacz", "ak47"), false, true), parse("{\"tacz_gun\": \"tacz:ak47\"}"));
        assertEquals(new ItemSpec.Gun(new ResourceLocation("tacz", "ak47"), true, false),
                parse("{\"tacz_gun\": \"tacz:ak47\", \"unlimited_ammo\": true, \"punchable\": false}"));
    }

    @Test
    void anyItemWithCountAndNbt() {
        ItemSpec spec = parse("{\"item\": \"minecraft:golden_apple\", \"count\": 2, \"nbt\": {\"pap\": {\"punchable\": true}}}");
        ItemSpec.Plain plain = assertInstanceOf(ItemSpec.Plain.class, spec);
        assertEquals(new ResourceLocation("minecraft", "golden_apple"), plain.item());
        assertEquals(2, plain.count());
        assertTrue(plain.nbt().orElseThrow().getCompound("pap").getBoolean("punchable"));
        assertEquals(new ItemSpec.Plain(new ResourceLocation("minecraft", "stick"), 1, Optional.empty()), parse("{\"item\": \"minecraft:stick\"}"));
    }

    /** Config lists, such as players.starting_items, write items the way /give takes them. */
    @Test
    void theGiveFormCarriesNbtAndRoundTrips() {
        ItemSpec.Plain dagger = ItemSpec.Plain.parse("lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}").getOrThrow(false, e -> {});
        assertEquals(new ResourceLocation("lrtactical", "melee"), dagger.item());
        assertEquals("lrtactical:dagger", dagger.nbt().orElseThrow().getString("MeleeWeaponId"));
        assertEquals(dagger, ItemSpec.Plain.parse(dagger.asGive()).getOrThrow(false, e -> {}));
        assertEquals("minecraft:iron_sword", ItemSpec.Plain.parse("minecraft:iron_sword").getOrThrow(false, e -> {}).asGive());
        assertTrue(ItemSpec.Plain.parse("lrtactical:melee{MeleeWeaponId:").error().isPresent(), "unclosed NBT");
        assertTrue(ItemSpec.Plain.parse("Not An Id").error().isPresent());
    }

    /** Wherever a datapack takes an item, the /give form works in place of the object. */
    @Test
    void datapackItemsTakeTheGiveForm() {
        ItemSpec.Plain dagger = assertInstanceOf(ItemSpec.Plain.class, parse("\"lrtactical:melee{MeleeWeaponId:\\\"lrtactical:dagger\\\"}\""));
        assertEquals("lrtactical:dagger", dagger.nbt().orElseThrow().getString("MeleeWeaponId"));
        assertEquals(dagger, parse(ItemSpec.CODEC.encodeStart(JsonOps.INSTANCE, dagger).getOrThrow(false, e -> {}).toString()), "written back as an object");
    }

    @Test
    void aGunCarriesNbtToMergeOntoIt() {
        ItemSpec.Gun gun = assertInstanceOf(ItemSpec.Gun.class, parse("{\"tacz_gun\": \"tacz:glock_17\", \"nbt\": {\"GunFireMode\": \"SEMI\"}}"));
        assertEquals("SEMI", gun.nbt().orElseThrow().getString("GunFireMode"));
        ItemSpec.Gun given = ItemSpec.Gun.parse("tacz:glock_17{GunFireMode:\"SEMI\"}").getOrThrow(false, e -> {});
        assertEquals(gun, given);
        assertEquals(given, ItemSpec.Gun.parse(given.asGive()).getOrThrow(false, e -> {}));
        assertEquals(ItemSpec.Gun.of(new ResourceLocation("tacz", "m1911")), ItemSpec.Gun.parse("tacz:m1911").getOrThrow(false, e -> {}));
    }

    @Test
    void roundTripsAndRejectsBadValues() {
        for (String json : new String[] {"{\"tacz_gun\": \"tacz:m4a1\"}", "{\"item\": \"minecraft:stick\", \"count\": 3}"}) {
            ItemSpec spec = parse(json);
            assertEquals(spec, parse(ItemSpec.CODEC.encodeStart(JsonOps.INSTANCE, spec).getOrThrow(false, e -> {}).toString()));
        }
        assertTrue(ItemSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"item\": \"minecraft:stick\", \"count\": 99}")).error().isPresent(),
                "a count over 64 is an error, not silently 1");
        assertTrue(ItemSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 2}")).error().isPresent());
    }
}
