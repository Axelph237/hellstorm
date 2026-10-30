package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.client.hud.RomanNumerals.Glyph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.hellstorm.client.hud.RomanNumerals.Glyph.*;
import static org.junit.jupiter.api.Assertions.*;

class RomanNumeralsTest {
    private static List<Glyph> of(int n) {
        return RomanNumerals.glyphs(n).orElseThrow();
    }

    private static List<Integer> xs(List<Glyph> glyphs) {
        return RomanNumerals.layout(glyphs).glyphs().stream().map(RomanNumerals.Placed::x).toList();
    }

    @Test
    void roundsAreSpeltTheStandardWay() {
        assertEquals(List.of(I), of(1));
        assertEquals(List.of(I, I, I), of(3));
        assertEquals(List.of(I, V), of(4));
        assertEquals(List.of(V, I, I, I), of(8));
        assertEquals(List.of(I, X), of(9));
        assertEquals(List.of(X, L), of(40));
        assertEquals(List.of(X, C, I, X), of(99));
        assertEquals(List.of(C), of(100));
        assertEquals(List.of(C, C, C, X, C, I, X), of(399));
    }

    @Test
    void thereAreNoNumeralsPastWhatTheGlyphsReach() {
        assertEquals(Optional.empty(), RomanNumerals.glyphs(0));
        assertEquals(Optional.empty(), RomanNumerals.glyphs(400));
    }

    @Test
    void onesOverlapEachOther() {
        assertEquals(List.of(0, 4, 8), xs(of(3)));
        assertEquals(13, RomanNumerals.layout(of(3)).width());
    }

    @Test
    void aSmallerGlyphBeforeALargerOneOverlapsIt() {
        assertEquals(List.of(0, 4), xs(List.of(I, V)), "IV");
        assertEquals(List.of(0, 6), xs(List.of(V, L)), "VL");
    }

    @Test
    void aLargerGlyphBeforeASmallerOneHasAGap() {
        assertEquals(List.of(0, 8), xs(List.of(L, V)), "LV");
        assertEquals(List.of(0, 8), xs(List.of(X, X)), "XX");
    }

    @Test
    void theUsersExampleOverlapsIVCAndIIIWithAGapBetween() {
        assertEquals(List.of(0, 4, 10, 18, 22, 26), xs(List.of(I, V, C, I, I, I)));
    }
}
