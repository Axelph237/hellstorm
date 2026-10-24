package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client's game time follows the server's, so it jumps, back as well as forward, when the server lags. Animations
 * and sounds timed by it stutter or replay, so client code keeps its own count of ticks instead.
 */
class ClientTimingLintTest {
    @Test
    void clientCodeDoesNotTimeAnythingByTheGameTime() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/candycanecazoo/hellstorm/client"))) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return Files.readString(p).contains("getGameTime()");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
            assertEquals(List.of(), offenders, "count client ticks on the block entity, as VendingMachineBlock.Entity's clientTicks does");
        }
    }
}
