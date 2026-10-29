package dev.candycanecazoo.hellstorm.client.ui;

import dev.candycanecazoo.hellstorm.client.ui.UiKit.SpinnerLook;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class UiKitTest {
    @Test
    void aSpinnerTakesItsRowFromTheBoxAndTheMouse() {
        assertEquals(0, SpinnerLook.of(false, false, false, false).ordinal(), "standard");
        assertEquals(1, SpinnerLook.of(false, false, true, false).ordinal(), "box hovered");
        assertEquals(2, SpinnerLook.of(false, false, true, true).ordinal(), "spinner hovered");
        assertEquals(3, SpinnerLook.of(false, true, false, false).ordinal(), "focused");
        assertEquals(4, SpinnerLook.of(false, true, true, false).ordinal(), "focused and hovered");
        assertEquals(5, SpinnerLook.of(false, true, true, true).ordinal(), "focused and spinner hovered");
        assertEquals(6, SpinnerLook.of(true, false, true, true).ordinal(), "disabled, hover or not");
        assertEquals(7, SpinnerLook.of(true, true, false, false).ordinal(), "disabled and focused");
    }

    @Test
    void theSheetsAreTheSizesTheirLooksAssume() throws IOException {
        assertSize("input_box", 2 * 24, 3 * 24);
        assertSize("switch", 4 * UiKit.SWITCH_SIZE, 3 * UiKit.SWITCH_SIZE);
        assertSize("spinners", 2 * UiKit.SPINNER, SpinnerLook.values().length * UiKit.SPINNER);
    }

    private static void assertSize(String sheet, int width, int height) throws IOException {
        try (InputStream in = UiKitTest.class.getResourceAsStream("/assets/hellstorm/textures/gui/" + sheet + ".png")) {
            BufferedImage image = ImageIO.read(in);
            assertEquals(width, image.getWidth(), sheet + " width");
            assertEquals(height, image.getHeight(), sheet + " height");
        }
    }
}
