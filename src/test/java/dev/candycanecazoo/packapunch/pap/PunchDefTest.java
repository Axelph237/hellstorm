package dev.candycanecazoo.packapunch.pap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PunchDefTest {
    private static PunchDef parse(String json) {
        return PunchDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, e -> {});
    }

    private static boolean invalid(String json) {
        return PunchDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent();
    }

    @Test
    void readsEveryField() {
        PunchDef def = parse("""
                { "result": { "tacz_gun": "tacz:m4a1" }, "name": "item.mymap.m4_upgraded", "cost": 4000,
                  "carry_over": { "ammo": false, "attachments": true } }""");
        assertEquals(new ItemSpec.Gun(ResourceLocation.tryBuild("tacz", "m4a1"), false, true), def.result());
        assertEquals(Optional.of("item.mymap.m4_upgraded"), def.name());
        assertEquals(Optional.of(4000), def.cost());
        assertEquals(new PunchDef.CarryOver(false, true), def.carryOver());
    }

    @Test
    void onlyTheResultIsRequired() {
        PunchDef def = parse("{ \"result\": { \"item\": \"minecraft:diamond_sword\" } }");
        assertEquals(Optional.empty(), def.name());
        assertEquals(Optional.empty(), def.cost());
        assertEquals(PunchDef.CarryOver.NONE, def.carryOver());
        assertEquals(new PunchDef.CarryOver(true, false), parse("{ \"result\": { \"item\": \"minecraft:stick\" }, \"carry_over\": { \"ammo\": true } }").carryOver());
    }

    @Test
    void invalidValuesAreErrorsNotDefaults() {
        assertTrue(invalid("{}"), "no result");
        assertTrue(invalid("{ \"result\": { \"count\": 2 } }"), "a result that is neither an item nor a gun");
        assertTrue(invalid("{ \"result\": { \"item\": \"minecraft:stick\" }, \"cost\": -1 }"));
        assertTrue(invalid("{ \"result\": { \"item\": \"minecraft:stick\" }, \"cost\": \"cheap\" }"));
        assertTrue(invalid("{ \"result\": { \"item\": \"minecraft:stick\" }, \"name\": 7 }"));
        assertTrue(invalid("{ \"result\": { \"item\": \"minecraft:stick\" }, \"carry_over\": { \"ammo\": \"yes\" } }"));
    }

    @Test
    void roundTrips() {
        PunchDef def = parse("{ \"result\": { \"tacz_gun\": \"tacz:ak47\" }, \"cost\": 10, \"carry_over\": { \"attachments\": true } }");
        assertEquals(def, parse(PunchDef.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow(false, e -> {}).toString()));
    }

    @Test
    void theShippedDefaultsParse() throws java.io.IOException {
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/data/packapunch/packapunch/punch"))) {
            for (java.nio.file.Path file : files.toList()) {
                assertDoesNotThrow(() -> parse(java.nio.file.Files.readString(file)), file.toString());
            }
        }
    }

    @Test
    void badFilesAreSkippedAndGoodOnesKept() {
        ResourceLocation good = ResourceLocation.tryBuild("mymap", "good");
        Map<ResourceLocation, JsonElement> files = Map.of(
                good, JsonParser.parseString("{ \"result\": { \"item\": \"minecraft:stick\" } }"),
                ResourceLocation.tryBuild("mymap", "bad"), JsonParser.parseString("{ \"result\": { \"item\": \"minecraft:stick\" }, \"cost\": -5 }"));
        assertEquals(java.util.Set.of(good), PunchDefs.parse(files).keySet());
    }
}
