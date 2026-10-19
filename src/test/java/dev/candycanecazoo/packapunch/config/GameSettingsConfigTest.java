package dev.candycanecazoo.packapunch.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSettingsConfigTest {
    /** Catches a config entry wired to the wrong field, or a default that drifted from GameSettings.DEFAULT. */
    @Test
    void aFreshConfigFileGivesTheDefaultSettings() {
        assertEquals(GameSettings.DEFAULT, ServerConfig.GAME.defaults());
    }
}
