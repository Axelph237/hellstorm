package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** The client resets and receives maps while a world is still loading, before Forge loads the server config. */
class ClientMapStateTest {
    @Test
    void resetAndSetMapWorkBeforeTheConfigIsLoaded() {
        assertDoesNotThrow(ClientMapState::reset);
        assertDoesNotThrow(() -> ClientMapState.setMap(ZombiesMap.EMPTY.withZone(
                new Zone(new ZoneId("start"), List.of(new BlockBox(0, 0, 0, 3, 3, 3)), true))));
    }
}
