package dev.candycanecazoo.hellstorm.util;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CodecLintTest {
    private record Sample(int count) {
        static final Codec<Sample> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.INT, "count", 3).forGetter(Sample::count)).apply(i, Sample::new));
    }

    @Test
    void absentUsesTheDefaultButInvalidIsAnError() {
        assertEquals(new Sample(3), Sample.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow(false, AssertionError::new));
        assertEquals(new Sample(7), Sample.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 7}")).getOrThrow(false, AssertionError::new));
        assertTrue(Sample.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": \"seven\"}")).error().isPresent());
    }

    /** Source files outside {@code allowed} that contain {@code text}. */
    private static List<Path> using(String text, List<Path> allowed) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(p -> p.toString().endsWith(".java") && allowed.stream().noneMatch(p::endsWith))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(text);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    void lenientOptionalFieldsAreNotUsed() throws IOException {
        assertEquals(List.of(), using(".optionalFieldOf(", List.of(Path.of("util", "Codecs.java"))),
                "use Codecs.strictOptional: optionalFieldOf silently ignores invalid values in 1.20.1");
    }

    /** World saves load what they can; datapack files go through DataFiles, which skips a file with any error. */
    @Test
    void partialResultsAreOnlyKeptFromWorldSaves() throws IOException {
        assertEquals(List.of(), using(".resultOrPartial(", List.of(Path.of("map", "MapSavedData.java"), Path.of("feature", "ConfigBlockEntity.java"))),
                "use DataFiles.parse: resultOrPartial keeps an out-of-range value with only a log line");
    }
}
