package dev.candycanecazoo.packapunch.powerup;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.packapunch.game.TimedEffects;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpDefTest {
    private static final Path DEFAULTS = Path.of("src/main/resources/data/packapunch/packapunch/powerups");

    private static DataResult<PowerUpDef> parse(String json) {
        return PowerUpDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void theSixDefaultsParseWithTheirOwnEffect() throws IOException {
        Map<String, PowerUpDef> defaults = new TreeMap<>();
        try (Stream<Path> files = Files.list(DEFAULTS)) {
            for (Path file : files.toList()) {
                defaults.put(file.getFileName().toString().replace(".json", ""),
                        parse(Files.readString(file)).getOrThrow(false, AssertionError::new));
            }
        }
        assertEquals(java.util.Set.of("max_ammo", "insta_kill", "double_points", "nuke", "carpenter", "fire_sale"), defaults.keySet());
        defaults.forEach((id, def) -> {
            assertEquals(id, def.effect().getSerializedName(), "each default carries the effect it's named for");
            assertEquals("powerup.packapunch." + id, def.name(), "names are lang keys");
        });
        assertEquals(Optional.of(TimedEffects.INSTA_KILL), defaults.get("insta_kill").effect().timer());
        assertEquals(Optional.empty(), defaults.get("nuke").effect().timer());
    }

    @Test
    void optionalFieldsDefaultAndBadEffectsFail() {
        PowerUpDef def = parse("{\"name\": \"powerup.test\", \"effect\": {\"type\": \"double_points\"}}").getOrThrow(false, AssertionError::new);
        assertEquals(10, def.weight());
        assertEquals(600, def.durationTicks());
        assertEquals(ResourceLocation.tryBuild("minecraft", "nether_star"), def.item());
        assertTrue(parse("{\"name\": \"x\", \"effect\": {\"type\": \"free_pizza\"}}").error().isPresent(), "unknown effect");
        assertTrue(parse("{\"name\": \"x\"}").error().isPresent(), "no effect");
        assertTrue(parse("{\"name\": \"x\", \"weight\": -1, \"effect\": {\"type\": \"nuke\"}}").error().isPresent(), "negative weight");
    }
}
