package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameHudTest {
    @Test
    void aNameOfSixLettersOrFewerIsShownWhole() {
        assertEquals("Steve", GameHud.shortName("Steve"));
        assertEquals("Notchy", GameHud.shortName("Notchy"));
    }

    @Test
    void aLongerNameIsCutToSixLettersAndEndsInDots() {
        assertEquals("candyc...", GameHud.shortName("candycanecazoo"));
        assertEquals("Alexan...", GameHud.shortName("Alexand"));
    }
}
