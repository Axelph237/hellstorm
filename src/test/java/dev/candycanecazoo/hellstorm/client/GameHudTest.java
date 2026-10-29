package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.config.ClientConfig.PlayerListPosition;
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

    @Test
    void theListSitsAtTheTopMiddleOrBottomOfItsSide() {
        assertEquals(8, PlayerListPosition.TOP_RIGHT.top(240, 42, 8, 0));
        assertEquals(99, PlayerListPosition.CENTER_RIGHT.top(240, 42, 8, 0));
        assertEquals(190, PlayerListPosition.BOTTOM_LEFT.top(240, 42, 8, 0));
        assertTrue(PlayerListPosition.CENTER_RIGHT.right && !PlayerListPosition.CENTER_LEFT.right);
    }

    @Test
    void atTheTopTheListKeepsBelowWhatIsAlreadyThere() {
        assertEquals(40, PlayerListPosition.TOP_LEFT.top(240, 42, 8, 40), "under the round");
        assertEquals(99, PlayerListPosition.CENTER_LEFT.top(240, 42, 8, 40), "the middle ignores it");
    }
}
