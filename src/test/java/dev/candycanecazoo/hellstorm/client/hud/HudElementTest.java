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
        assertTrue(spec.getSpec().contains("zombies.attached"), "the zombies left can sit under the round");
        assertFalse(spec.getSpec().contains("round.attached"), "the round sits under nothing");
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

    @Test
    void pointsAreWrittenWithADollarSign() {
        assertEquals("$1500", PlayerBoard.points(new dev.candycanecazoo.hellstorm.net.HudSyncPacket.Line("Steve", 1500, true, false, true)));
    }

    @Test
    void anAttachedPanelSitsJustUnderTheOtherLinedUpTheWayItIs() {
        HudElement.Panel round = new HudElement.Panel(8, 8, 40, 24);
        assertEquals(new HudElement.Panel(8, 8 + 24 + HudElement.ATTACHED_GAP, 30, 16), HudElement.attachedUnder(round, 0, 30, 16, 400), "left edges together");
        assertEquals(new HudElement.Panel(18, 34, 30, 16), HudElement.attachedUnder(round, 1, 30, 16, 400), "right edges together");
        assertEquals(new HudElement.Panel(13, 34, 30, 16), HudElement.attachedUnder(round, 0.5, 30, 16, 400), "centres together");
    }

    @Test
    void anAttachedPanelWiderThanTheOneAboveStaysOnScreen() {
        HudElement.Panel round = new HudElement.Panel(8, 8, 40, 24);
        assertEquals(HudElement.MARGIN, HudElement.attachedUnder(round, 1, 80, 16, 400).x());
    }
}
