package dev.candycanecazoo.packapunch.feature;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardLayoutTest {
    private static final int[][] SIZES = {{1, 1}, {1, 2}, {2, 2}, {3, 2}, {5, 1}, {5, 4}, {1, 4}};

    /** The board's centre line from end to end. */
    private static double[][] ends(BoardLayout.Board b) {
        double du = Math.cos(b.angle()) * b.length() / 2;
        double dv = Math.sin(b.angle()) * b.length() / 2;
        return new double[][] {{b.u() - du, b.v() - dv}, {b.u() + du, b.v() + dv}};
    }

    @Test
    void oneBoardPerBoardSlotAndEachSpansTheWholeWidth() {
        for (int[] size : SIZES) {
            for (int boards = 1; boards <= 6; boards++) {
                List<BoardLayout.Board> layout = BoardLayout.of("w", size[0], size[1], boards);
                assertEquals(boards, layout.size());
                for (BoardLayout.Board b : layout) {
                    double[][] ends = ends(b);
                    assertTrue(ends[0][0] < 0 && ends[1][0] > size[0], "board doesn't cross a " + size[0] + "x" + size[1] + " window: " + b);
                }
            }
        }
    }

    @Test
    void boardsStayInsideTheOpeningTopToBottom() {
        for (int[] size : SIZES) {
            for (String id : List.of("a", "hall1", "room1", "start1", "x")) {
                for (BoardLayout.Board b : BoardLayout.of(id, size[0], size[1], 6)) {
                    double reach = Math.abs(Math.sin(b.angle())) * b.length() / 2 + Math.cos(b.angle()) * b.width() / 2;
                    assertTrue(b.v() - reach >= 0 && b.v() + reach <= size[1],
                            "board pokes out of a " + size[0] + "x" + size[1] + " window " + id + ": " + b);
                }
            }
        }
    }

    @Test
    void boardsAreSlantedAndAlternateDirection() {
        for (int[] size : SIZES) {
            List<BoardLayout.Board> layout = BoardLayout.of("hall1", size[0], size[1], 6);
            long slanted = layout.stream().filter(b -> Math.abs(b.angle()) >= Math.toRadians(5)).count();
            assertTrue(slanted >= 2, size[0] + "x" + size[1] + " has only " + slanted + " visibly slanted boards");
            for (int i = 0; i < layout.size(); i++) {
                BoardLayout.Board b = layout.get(i);
                assertTrue(i % 2 == 0 ? b.angle() >= 0 : b.angle() <= 0, "alternating slant");
                assertEquals(i % 2, b.layer(), "each slant direction on its own layer");
            }
        }
    }

    @Test
    void boardsSpreadFromBottomToTop() {
        for (int[] size : SIZES) {
            List<BoardLayout.Board> layout = BoardLayout.of("start1", size[0], size[1], 6);
            double low = layout.stream().mapToDouble(BoardLayout.Board::v).min().orElseThrow();
            double high = layout.stream().mapToDouble(BoardLayout.Board::v).max().orElseThrow();
            assertTrue(low < size[1] * 0.25 && high > size[1] * 0.75, size[0] + "x" + size[1] + " boards bunch up: " + low + ".." + high);
        }
    }

    @Test
    void theSameWindowAlwaysLooksTheSameAndDifferentWindowsDiffer() {
        assertEquals(BoardLayout.of("hall1", 2, 2, 6), BoardLayout.of("hall1", 2, 2, 6));
        assertNotEquals(BoardLayout.of("hall1", 2, 2, 6), BoardLayout.of("room1", 2, 2, 6));
    }
}
