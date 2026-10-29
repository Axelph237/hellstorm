package dev.candycanecazoo.hellstorm.client.hud;

import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudElementTest {
    @Test
    void zeroAndOnePutTheBoxAgainstTheEdgesInsideTheMargin() {
        assertEquals(HudElement.MARGIN, HudElement.place(0, 400, 50));
        assertEquals(400 - HudElement.MARGIN - 50, HudElement.place(1, 400, 50));
    }

    @Test
    void aHalfCentresTheBox() {
        assertEquals((400 - 50) / 2, HudElement.place(0.5, 400, 50));
    }

    @Test
    void aBoxWiderThanTheScreenStartsAtTheMarginWherever() {
        assertEquals(HudElement.MARGIN, HudElement.place(1, 40, 50));
    }

    @Test
    void everyElementHasItsOwnSectionWithAPositionAndHidden() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        HudElements.ALL.forEach(element -> element.define(builder));
        ForgeConfigSpec spec = builder.build();
        for (HudElement element : HudElements.ALL) {
            for (String key : List.of("x", "y", "hidden")) {
                assertTrue(spec.getSpec().contains(element.id() + "." + key), element.id() + " has no " + key);
            }
        }
        assertTrue(spec.getSpec().contains("players.justify"), "the player board has its order");
    }

    @Test
    void aNameOfSixLettersOrFewerIsShownWhole() {
        assertEquals("Steve", PlayerBoard.shortName("Steve"));
        assertEquals("Notchy", PlayerBoard.shortName("Notchy"));
    }

    @Test
    void aLongerNameIsCutToSixLettersAndEndsInDots() {
        assertEquals("candyc...", PlayerBoard.shortName("candycanecazoo"));
        assertEquals("Alexan...", PlayerBoard.shortName("Alexand"));
    }
}
