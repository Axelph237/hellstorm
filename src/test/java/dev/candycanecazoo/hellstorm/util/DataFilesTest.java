package dev.candycanecazoo.hellstorm.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.mob.MobProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DataFilesTest {
    private record Sample(int weight, String name) {
        static final Codec<Sample> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.intRange(0, 100), "weight", 10).forGetter(Sample::weight),
                Codecs.strictOptional(Codec.STRING, "name", "").forGetter(Sample::name)).apply(i, Sample::new));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild("test", path);
    }

    private static Map<ResourceLocation, JsonElement> files(String... idsAndJson) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        for (int i = 0; i < idsAndJson.length; i += 2) {
            files.put(id(idsAndJson[i]), JsonParser.parseString(idsAndJson[i + 1]));
        }
        return files;
    }

    @Test
    void aFileWithAnOutOfRangeValueIsSkippedWholeAndItsErrorKept() {
        Map<ResourceLocation, Sample> loaded = DataFiles.parse("sample", Sample.CODEC, files(
                "good", "{\"weight\": 3, \"name\": \"ok\"}",
                "negative", "{\"weight\": -1, \"name\": \"kept by resultOrPartial\"}",
                "wrong_type", "{\"name\": 5}"));
        assertEquals(Map.of(id("good"), new Sample(3, "ok")), loaded);
        List<String> errors = DataFiles.errors().get("sample");
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("test:negative: weight: ") && errors.get(0).contains("-1"), errors.get(0));
        assertTrue(errors.get(1).startsWith("test:wrong_type: name: "), errors.get(1));
    }

    @Test
    void aFileTheLoaderRefusesIsSkippedToo() {
        Map<ResourceLocation, Sample> loaded = DataFiles.parse("refused", Sample.CODEC, files("a", "{\"name\": \"a\"}", "b", "{\"name\": \"b\"}"),
                sample -> sample.name().equals("b") ? Optional.of("b is not allowed") : Optional.empty());
        assertEquals(List.of(id("a")), List.copyOf(loaded.keySet()));
        assertEquals(List.of("test:b: b is not allowed"), DataFiles.errors().get("refused"));
    }

    @Test
    void aCleanReloadClearsTheKindsErrors() {
        DataFiles.parse("reloaded", Sample.CODEC, files("bad", "{\"weight\": 101}"));
        assertEquals(1, DataFiles.errors().get("reloaded").size());
        DataFiles.parse("reloaded", Sample.CODEC, files("bad", "{\"weight\": 100}"));
        assertEquals(List.of(), DataFiles.errors().get("reloaded"));
    }

    @Test
    void aMobProfileThatWouldFreezeItsZombiesIsSkipped() {
        Map<ResourceLocation, MobProfile> loaded = DataFiles.parse("mob profile", MobProfile.CODEC, files(
                "frozen", "{\"entity\": \"minecraft:zombie\", \"speed_multiplier\": 0}",
                "skewed", "{\"entity\": \"minecraft:zombie\", \"weight\": -1}",
                "zombie", "{\"entity\": \"minecraft:zombie\"}"));
        assertEquals(List.of(id("zombie")), List.copyOf(loaded.keySet()));
        assertEquals(2, DataFiles.errors().get("mob profile").size());
    }
}
