package dev.candycanecazoo.hellstorm.feature;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BoxSpinTest {
    private static final int ROLL = 80;
    private static final int FRAMES_PER_TICK = 3;

    /** The flick shown at each of a client's frames, -1 while the lid opens, over {@code ticks} client ticks. */
    private static List<Integer> frames(BoxSpin spin, int ticks, Runnable beforeTick) {
        List<Integer> shown = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            beforeTick.run();
            spin.tick();
            for (int f = 0; f < FRAMES_PER_TICK; f++) {
                shown.add(spin.rise((float) f / FRAMES_PER_TICK).map(BoxSpin::flick).orElse(-1));
            }
        }
        return shown;
    }

    private static void assertInOrder(List<Integer> shown) {
        for (int i = 1; i < shown.size(); i++) {
            assertTrue(shown.get(i) >= shown.get(i - 1), "the gun went back from flick " + shown.get(i - 1) + " to " + shown.get(i) + " at frame " + i);
        }
    }

    @Test
    void theGunsRiseInOrderAndSettleWhenTheRollEnds() {
        BoxSpin spin = new BoxSpin();
        spin.receive(1000, ROLL, 0);
        List<Integer> shown = frames(spin, ROLL + 10, () -> {});
        assertInOrder(shown);
        int firstRising = shown.indexOf(0) / FRAMES_PER_TICK;
        assertEquals(MysteryBoxBlock.LID_TICKS - 1, firstRising, "the guns rise once the lid is open");
        assertEquals(MysteryBoxBlock.FLICKS, shown.get((ROLL - 1) * FRAMES_PER_TICK), "the last gun settles when the roll ends");
        assertEquals(MysteryBoxBlock.FLICKS + 1, shown.stream().filter(i -> i >= 0).distinct().count(), "every flick shows");
    }

    /**
     * A server running behind sends the same roll again partway (a Fire Sale changed its price, say) and is fewer ticks in
     * than the client; a jittery one may be more. Neither may move the guns.
     */
    @Test
    void theSameRollSentAgainDoesNotMoveTheGuns() {
        for (int serverElapsed : new int[] {30, 60}) {
            BoxSpin steady = new BoxSpin();
            steady.receive(1000, ROLL, 0);
            List<Integer> expected = frames(steady, ROLL, () -> {});

            BoxSpin resent = new BoxSpin();
            resent.receive(1000, ROLL, 0);
            int[] tick = {0};
            List<Integer> shown = frames(resent, ROLL, () -> {
                if (tick[0]++ == 45) {
                    resent.receive(1000, ROLL, serverElapsed);
                }
            });
            assertEquals(expected, shown, "resent at elapsed " + serverElapsed);
        }
    }

    @Test
    void aPlayerWhoArrivesMidRollPicksUpWhereTheServerIs() {
        BoxSpin spin = new BoxSpin();
        spin.receive(1000, ROLL, 50);
        assertEquals(BoxSpin.rise(50.5f, ROLL), spin.rise(0.5f));
        spin.tick();
        assertEquals(BoxSpin.rise(51, ROLL), spin.rise(0));
    }

    @Test
    void aNewRollStartsOver() {
        BoxSpin spin = new BoxSpin();
        spin.receive(1000, ROLL, 0);
        frames(spin, ROLL, () -> {});
        spin.receive(1300, ROLL, 0);
        assertEquals(Optional.empty(), spin.rise(0), "the lid opens again first");
    }

    @Test
    void nothingRisesWithoutARoll() {
        BoxSpin spin = new BoxSpin();
        spin.tick();
        assertEquals(Optional.empty(), spin.rise(0));
        spin.receive(1000, ROLL, 40);
        spin.stop();
        spin.tick();
        assertEquals(Optional.empty(), spin.rise(0));
    }

    @Test
    void theRiseEasesFromTheLidToTheTop() {
        assertEquals(Optional.empty(), BoxSpin.rise(MysteryBoxBlock.LID_TICKS - 0.01f, ROLL));
        assertEquals(Optional.of(0f), BoxSpin.rise(MysteryBoxBlock.LID_TICKS, ROLL));
        assertEquals(Optional.of(0.75f), BoxSpin.rise((MysteryBoxBlock.LID_TICKS + ROLL) / 2f, ROLL), "half the time, three quarters of the way");
        assertEquals(Optional.of(1f), BoxSpin.rise(ROLL, ROLL));
        assertEquals(Optional.of(1f), BoxSpin.rise(ROLL + 20, ROLL), "held at the top until the server offers the result");
        assertEquals(MysteryBoxBlock.FLICKS, BoxSpin.flick(1));
    }
}
