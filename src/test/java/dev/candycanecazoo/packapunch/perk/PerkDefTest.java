package dev.candycanecazoo.packapunch.perk;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PerkDefTest {
    private static final Path DEFAULTS = Path.of("src/main/resources/data/packapunch/packapunch/perks");

    private static DataResult<PerkDef> parse(String json) {
        return PerkDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static PerkDef valid(String json) {
        return parse(json).getOrThrow(false, AssertionError::new);
    }

    private static void invalid(String json, String why) {
        assertTrue(parse(json).error().isPresent(), why);
    }

    private static String withEffect(String effect) {
        return "{\"name\": \"perk.test\", \"cost\": 100, \"effects\": [" + effect + "]}";
    }

    @Test
    void theDefaultPerksParseWithCoDCosts() throws IOException {
        Map<String, PerkDef> defaults = new TreeMap<>();
        try (Stream<Path> files = Files.list(DEFAULTS)) {
            for (Path file : files.toList()) {
                defaults.put(file.getFileName().toString().replace(".json", ""), valid(Files.readString(file)));
            }
        }
        Map<String, Integer> costs = new TreeMap<>();
        defaults.forEach((id, def) -> costs.put(id, def.cost()));
        assertEquals(Map.of("juggernog", 2500, "speed_cola", 3000, "double_tap", 2000, "stamin_up", 2000, "quick_revive", 1500, "mule_kick", 4000), costs);
        defaults.forEach((id, def) -> assertEquals("perk.packapunch." + id, def.name(), "names are lang keys"));
        assertFalse(defaults.get("quick_revive").requiresPower());
        assertTrue(defaults.get("juggernog").requiresPower());
        assertEquals(0xd33a2c, defaults.get("juggernog").color());
        assertEquals(List.of(new PerkEffect.Attribute(ResourceLocation.tryBuild("minecraft", "generic.max_health"), 20, AttributeModifier.Operation.ADDITION)),
                defaults.get("juggernog").effects());
    }

    @Test
    void optionalFieldsDefault() {
        PerkDef def = valid("{\"name\": \"perk.test\", \"cost\": 0, \"effects\": []}");
        assertTrue(def.requiresPower());
        assertEquals(0xFFFFFF, def.color());
    }

    @Test
    void everyEffectTypeDispatchesToItsRecord() {
        Map<String, PerkEffect> expected = Map.of(
                "{\"type\": \"attribute_modifier\", \"attribute\": \"minecraft:generic.movement_speed\", \"amount\": 0.1, \"operation\": \"multiply_base\"}",
                new PerkEffect.Attribute(ResourceLocation.tryBuild("minecraft", "generic.movement_speed"), 0.1, AttributeModifier.Operation.MULTIPLY_BASE),
                "{\"type\": \"attribute_modifier\", \"attribute\": \"minecraft:generic.armor\", \"amount\": 2}",
                new PerkEffect.Attribute(ResourceLocation.tryBuild("minecraft", "generic.armor"), 2, AttributeModifier.Operation.ADDITION),
                "{\"type\": \"damage_multiplier\", \"multiplier\": 2}", new PerkEffect.DamageMultiplier(2),
                "{\"type\": \"rpm_multiplier\", \"multiplier\": 1.33}", new PerkEffect.RpmMultiplier(1.33),
                "{\"type\": \"weapon_slots\", \"slots\": 1}", new PerkEffect.WeaponSlots(1),
                "{\"type\": \"self_revive\"}", new PerkEffect.SelfRevive());
        expected.forEach((json, effect) -> {
            PerkDef def = valid(withEffect(json));
            assertEquals(List.of(effect), def.effects(), json);
            JsonElement encoded = PerkDef.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow(false, AssertionError::new);
            assertEquals(def, PerkDef.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, AssertionError::new), "round trip of " + json);
        });
    }

    @Test
    void invalidValuesAreErrors() {
        invalid("{\"cost\": 100, \"effects\": []}", "name is required");
        invalid("{\"name\": \"perk.test\", \"cost\": -1, \"effects\": []}", "negative cost");
        invalid("{\"name\": \"perk.test\", \"cost\": 100}", "effects are required");
        invalid("{\"name\": \"perk.test\", \"cost\": 100, \"requires_power\": \"yes\", \"effects\": []}", "present but invalid power flag");
        invalid("{\"name\": \"perk.test\", \"cost\": 100, \"color\": \"red\", \"effects\": []}", "named color");
        invalid("{\"name\": \"perk.test\", \"cost\": 100, \"color\": \"#12345g\", \"effects\": []}", "bad hex digit");
        invalid("{\"name\": \"perk.test\", \"cost\": 100, \"color\": \"#+12345\", \"effects\": []}", "sign in color");
        invalid(withEffect("{\"type\": \"extra_lives\"}"), "unknown effect type");
        invalid(withEffect("{\"multiplier\": 2}"), "missing type");
        invalid(withEffect("{\"type\": \"rpm_multiplier\"}"), "missing multiplier");
        invalid(withEffect("{\"type\": \"rpm_multiplier\", \"multiplier\": 0}"), "zero multiplier");
        invalid(withEffect("{\"type\": \"weapon_slots\", \"slots\": 0}"), "zero slots");
        invalid(withEffect("{\"type\": \"attribute_modifier\", \"attribute\": \"minecraft:generic.armor\", \"amount\": 2, \"operation\": \"add\"}"),
                "unknown operation");
        invalid(withEffect("{\"type\": \"attribute_modifier\", \"amount\": 2}"), "missing attribute");
    }

    @Test
    void modifierIdsAreStablePerPerkAndEffect() {
        ResourceLocation jug = ResourceLocation.tryBuild("packapunch", "juggernog");
        assertEquals(PerkDef.modifierId(jug, 0), PerkDef.modifierId(ResourceLocation.tryBuild("packapunch", "juggernog"), 0));
        assertNotEquals(PerkDef.modifierId(jug, 0), PerkDef.modifierId(jug, 1));
        assertNotEquals(PerkDef.modifierId(jug, 0), PerkDef.modifierId(ResourceLocation.tryBuild("packapunch", "stamin_up"), 0));
    }
}
