package dev.candycanecazoo.packapunch.util;

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

    @Test
    void lenientOptionalFieldsAreNotUsed() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".java") && !p.endsWith(Path.of("util", "Codecs.java")))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(".optionalFieldOf(");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            assertEquals(List.of(), offenders, "use Codecs.strictOptional: optionalFieldOf silently ignores invalid values in 1.20.1");
        }
    }
}
