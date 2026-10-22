package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MachineShadesTest {
    /** The median of the machine's green, its most-used shade. */
    private static final int MEDIAN_GREEN = 0x5ab552;
    private static final int HEALING_RED = 0xf82423;

    private static int[] texture() throws IOException {
        try (InputStream in = MachineShadesTest.class.getResourceAsStream("/assets/hellstorm/textures/block/vending_machine.png")) {
            BufferedImage image = ImageIO.read(in);
            return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                    .filter(argb -> argb >>> 24 != 0).map(argb -> argb & 0xffffff).toArray();
        }
    }

    private static int[] greens() throws IOException {
        return Arrays.stream(texture()).filter(MachineShades::isGreen).distinct().toArray();
    }

    private static void assertClose(int expected, int actual) {
        for (int shift : new int[] {16, 8, 0}) {
            assertTrue(Math.abs((expected >> shift & 0xff) - (actual >> shift & 0xff)) <= 1,
                    String.format("expected #%06x, got #%06x", expected, actual));
        }
    }

    @Test
    void theMachinesMedianGreenIsItsMostUsedShade() throws IOException {
        MachineShades.Hsv median = MachineShades.median(texture()).orElseThrow();
        assertClose(MEDIAN_GREEN, median.rgb());
        assertEquals(6, greens().length, "the machine is painted in six greens");
    }

    @Test
    void theMedianGreenBecomesTheItemsColorExactly() throws IOException {
        MachineShades.Hsv median = MachineShades.median(texture()).orElseThrow();
        for (int color : new int[] {HEALING_RED, 0x385dc6, 0x7f7f7f, 0xffffff, 0x202020}) {
            assertClose(color, MachineShades.recolor(MEDIAN_GREEN, median, MachineShades.Hsv.of(color)));
        }
    }

    @Test
    void everyShadeStaysDistinctAndKeepsItsPlaceLighterOrDarker() throws IOException {
        MachineShades.Hsv median = MachineShades.median(texture()).orElseThrow();
        MachineShades.Hsv red = MachineShades.Hsv.of(HEALING_RED);
        List<Integer> byBrightness = Arrays.stream(greens()).boxed()
                .sorted(Comparator.comparingDouble(c -> MachineShades.Hsv.of(c).v())).toList();
        List<Float> painted = byBrightness.stream().map(c -> MachineShades.Hsv.of(MachineShades.recolor(c, median, red)).v()).toList();
        for (int i = 1; i < painted.size(); i++) {
            assertTrue(painted.get(i) >= painted.get(i - 1), "brightness order changed: " + painted);
        }
        assertEquals(6, byBrightness.stream().map(c -> MachineShades.recolor(c, median, red)).distinct().count(), "two shades merged");
        for (int c : byBrightness) {
            assertFalse(MachineShades.isGreen(MachineShades.recolor(c, median, red)), String.format("#%06x is still green", c));
        }
    }

    @Test
    void colorsThatArentTheGreenAreLeftAlone() throws IOException {
        MachineShades.Hsv median = MachineShades.median(texture()).orElseThrow();
        MachineShades.Hsv red = MachineShades.Hsv.of(HEALING_RED);
        int[] others = Arrays.stream(texture()).filter(c -> !MachineShades.isGreen(c)).distinct().toArray();
        assertTrue(others.length > 0);
        for (int c : others) {
            assertEquals(c, MachineShades.recolor(c, median, red));
        }
    }
}
