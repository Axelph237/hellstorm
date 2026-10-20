package dev.candycanecazoo.packapunch.data;

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
