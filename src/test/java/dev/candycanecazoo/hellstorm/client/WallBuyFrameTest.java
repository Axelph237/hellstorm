package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WallBuyFrameTest {
    private static WallBuyFrame frame() throws IOException {
        try (InputStream in = WallBuyFrameTest.class.getResourceAsStream("/assets/hellstorm/shapes/wall_buy.json")) {
            return WallBuyFrame.load(in);
        }
    }

    private static float start(List<WallBuyFrame.Box> boxes) {
        return (float) boxes.stream().mapToDouble(b -> b.from()[2]).min().orElseThrow();
    }

    private static float end(List<WallBuyFrame.Box> boxes) {
        return (float) boxes.stream().mapToDouble(b -> b.to()[2]).max().orElseThrow();
    }

    @Test
    void atItsOwnLengthTheFrameIsTheModelAsDrawn() throws IOException {
        List<WallBuyFrame.Box> boxes = frame().laidOut(16);
        assertEquals(17, boxes.size());
        assertEquals(0, start(boxes));
        assertEquals(16, end(boxes));
    }

    @Test
    void longerTheMiddleRepeatsWithoutStretchingAndTheCapsKeepTheirSize() throws IOException {
        WallBuyFrame frame = frame();
        List<WallBuyFrame.Box> boxes = frame.laidOut(37);
        assertEquals(0, start(boxes));
        assertEquals(37, end(boxes), 1e-4);
        for (WallBuyFrame.Box box : boxes) {
            assertTrue(box.to()[2] - box.from()[2] <= 10 + 1e-4, "no box is longer than the model's middle");
        }
        assertEquals(6, frame.capsLength(), 1e-4, "3-pixel caps at each end");
    }

    @Test
    void theMiddlesEndFacesShowOnlyAtTheEndsOfTheWholeMiddle() throws IOException {
        List<WallBuyFrame.Box> boxes = frame().laidOut(37);
        // At 37 the middle runs from 3 to 34, between the caps.
        long inner = boxes.stream().filter(b -> b.from()[2] > 3 + 1e-4 && b.to()[2] <= 34 + 1e-4 && b.faces().containsKey(WallBuyFrame.Side.NORTH)).count();
        long innerSouth = boxes.stream().filter(b -> b.from()[2] >= 3 - 1e-4 && b.to()[2] < 34 - 1e-4 && b.faces().containsKey(WallBuyFrame.Side.SOUTH)).count();
        assertEquals(0, inner, "no north face between copies of the middle");
        assertEquals(0, innerSouth, "no south face between copies of the middle");
    }

    @Test
    void aMiddleCutShortShowsOnlyThatMuchOfItsTexture() throws IOException {
        // At 18 the middle is 12 long: one whole copy, then 2 pixels of the next, from 13 to 15.
        WallBuyFrame.Box last = frame().laidOut(18).stream()
                .filter(b -> b.from()[2] >= 13 - 1e-4 && b.to()[2] <= 15 + 1e-4 && b.from()[0] == 1 && b.to()[0] == 15).findFirst().orElseThrow();
        assertEquals(2, last.to()[2] - last.from()[2], 1e-4);
        WallBuyFrame.Uv up = last.faces().get(WallBuyFrame.Side.UP);
        assertEquals(2, up.v1() - up.v0(), 1e-4, "two of the up face's ten pixels");
    }
}
