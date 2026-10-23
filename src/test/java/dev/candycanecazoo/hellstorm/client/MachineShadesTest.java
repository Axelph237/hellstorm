package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MachineShadesTest {
    /** The median of the machine's green, its most-used shade. */
    private static final int MEDIAN_GREEN = 0x5ab552;
    private static final int HEALING_RED = 0xf82423;
    private static final int LOAD_UP_GREEN = 0x3fae4a;

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
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        assertClose(MEDIAN_GREEN, basis.green().rgb());
        assertEquals(6, greens().length, "the machine is painted in six greens");
    }

    @Test
    void theMedianGreenBecomesTheItemsColorExactly() throws IOException {
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        for (int color : new int[] {HEALING_RED, 0x385dc6, 0x7f7f7f, 0xffffff, 0x202020}) {
            assertClose(color, MachineShades.recolor(MEDIAN_GREEN, basis, MachineShades.Hsv.of(color)));
        }
    }

    @Test
    void everyShadeStaysDistinctAndKeepsItsPlaceLighterOrDarker() throws IOException {
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        MachineShades.Hsv red = MachineShades.Hsv.of(HEALING_RED);
        List<Integer> byBrightness = Arrays.stream(greens()).boxed()
                .sorted(Comparator.comparingDouble(c -> MachineShades.Hsv.of(c).v())).toList();
        List<Float> painted = byBrightness.stream().map(c -> MachineShades.Hsv.of(MachineShades.recolor(c, basis, red)).v()).toList();
        for (int i = 1; i < painted.size(); i++) {
            assertTrue(painted.get(i) >= painted.get(i - 1), "brightness order changed: " + painted);
        }
        assertEquals(6, byBrightness.stream().map(c -> MachineShades.recolor(c, basis, red)).distinct().count(), "two shades merged");
        for (int c : byBrightness) {
            assertFalse(MachineShades.isGreen(MachineShades.recolor(c, basis, red)), String.format("#%06x is still green", c));
        }
    }

    @Test
    void onRedTheHighlightsLeanTowardYellowAndTheShadowsTowardBlueAsTheyDoOnGreen() throws IOException {
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        MachineShades.Hsv red = MachineShades.Hsv.of(HEALING_RED);
        float highlight = MachineShades.turn(red.h(), MachineShades.Hsv.of(MachineShades.recolor(0xb0e499, basis, red)).h());
        float shadow = MachineShades.turn(red.h(), MachineShades.Hsv.of(MachineShades.recolor(0x207841, basis, red)).h());
        assertTrue(highlight > 0.02 && highlight < 0.1, "the lightest green should turn red toward orange, turned " + highlight);
        assertTrue(shadow < -0.02 && shadow > -0.1, "the darkest green should turn red toward crimson, turned " + shadow);
    }

    @Test
    void theGreysKeepTheirPurpleNearGreenAndElsewhereTakeTheColorsHueKeepingTheirSaturation() throws IOException {
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        MachineShades.Hsv red = MachineShades.Hsv.of(HEALING_RED);
        int[] greys = Arrays.stream(texture()).filter(c -> MachineShades.role(c) == MachineShades.Role.GREY).distinct().toArray();
        assertTrue(greys.length >= 8, "the machine has its lavenders and navies");
        for (int c : greys) {
            assertClose(c, MachineShades.recolor(c, basis, basis.green()));
            assertClose(c, MachineShades.recolor(c, basis, MachineShades.Hsv.of(LOAD_UP_GREEN)));
            MachineShades.Hsv before = MachineShades.Hsv.of(c);
            MachineShades.Hsv after = MachineShades.Hsv.of(MachineShades.recolor(c, basis, red));
            assertEquals(before.s(), after.s(), 0.02, String.format("#%06x's saturation on red", c));
            assertEquals(before.v(), after.v(), 0.01, String.format("#%06x's brightness on red", c));
            assertTrue(Math.abs(MachineShades.turn(red.h(), after.h())) < 45 / 360f,
                    String.format("#%06x should be a grey of red, its hue is %.0f", c, after.h() * 360));
        }
        float navy = MachineShades.turn(red.h(), MachineShades.Hsv.of(MachineShades.recolor(0x3e3f66, basis, red)).h());
        float lavender = MachineShades.turn(red.h(), MachineShades.Hsv.of(MachineShades.recolor(0xb59dbf, basis, red)).h());
        assertTrue(navy < 0 && lavender > navy, "the navy should stay cooler than the lavender, toward crimson: " + navy + ", " + lavender);
    }

    @Test
    void theWhitesAndTheCyanLightsKeepTheirColors() throws IOException {
        MachineShades.Basis basis = MachineShades.basis(texture()).orElseThrow();
        for (int c : new int[] {0xf3f1f3, 0xd7cad6, 0xffffff, 0x5bbcf4, 0x36c5f4, 0x6dead6, 0x7ad4ff, 0xb4effd}) {
            for (int color : new int[] {HEALING_RED, 0x385dc6, 0xf2d64b, 0x202020}) {
                assertEquals(c, MachineShades.recolor(c, basis, MachineShades.Hsv.of(color)), String.format("#%06x on #%06x", c, color));
            }
        }
    }

    @Test
    void eachColorOfTheTexturesHasTheRoleItWasPaintedFor() {
        Map<MachineShades.Role, int[]> painted = Map.of(
                MachineShades.Role.BRAND, new int[] {0xb0e499, 0x90d67d, 0x5ab552, 0x2f9840, 0x207841},
                MachineShades.Role.GREY, new int[] {0xb59dbf, 0xa289b3, 0x9d92ab, 0x8c78a5, 0x8c75a5, 0x786899, 0xb0a7b8, 0x5e5b8c,
                        0x3e3f66, 0x3d3b66, 0x50567c},
                MachineShades.Role.WHITE, new int[] {0xffffff, 0xf3f1f3, 0xeae2eb, 0xe6e3e7, 0xd7cad6, 0xc8b2ca, 0xc5bdc9},
                MachineShades.Role.LIGHT, new int[] {0x5bbcf4, 0x7bd4ff, 0x36c5f4, 0x6dead6, 0x7ad4ff, 0xb4effd, 0xec273f, 0xfe6275});
        painted.forEach((role, colors) -> {
            for (int c : colors) {
                assertEquals(role, MachineShades.role(c), String.format("#%06x", c));
            }
        });
    }

    @Test
    void theKeypadsRedAndGreenLightsAreTheOnlyColorsLeftAlone() throws IOException {
        int[] lit;
        int width;
        int height;
        try (InputStream in = MachineShadesTest.class.getResourceAsStream("/assets/hellstorm/textures/block/vending_machine_on.png")) {
            BufferedImage image = ImageIO.read(in);
            width = image.getWidth();
            height = image.getHeight();
            lit = image.getRGB(0, 0, width, height, null, 0, width);
        }
        int lights = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = lit[y * width + x] & 0xffffff;
                if (lit[y * width + x] >>> 24 != 0 && MachineShades.keypadLight(x, y % width, rgb)) {
                    lights++;
                    assertTrue(x >= 58 && x <= 59 && (y % width == 58 || y % width == 59 || y % width == 61), "keypad light at " + x + ", " + y);
                }
            }
        }
        assertEquals(5 * height / width, lights, "five lights a frame, in every frame");
        assertFalse(MachineShades.keypadLight(58, 60, 0x8c78a5), "the keypad's own purple is repainted");
        assertFalse(MachineShades.keypadLight(10, 10, 0x9de64e), "a green like the keypad's elsewhere is repainted");
    }
}
