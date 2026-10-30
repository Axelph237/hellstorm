package dev.candycanecazoo.hellstorm.client.anim;

import dev.candycanecazoo.hellstorm.client.anim.Animation.Direction;
import dev.candycanecazoo.hellstorm.client.anim.Animation.Fill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    private static final float EPSILON = 1e-3f;

    @Test
    void easingsStartAtZeroEndAtOneAndBendTheWayCssDoes() {
        for (Easing easing : new Easing[] {Easing.LINEAR, Easing.EASE, Easing.EASE_IN, Easing.EASE_OUT, Easing.EASE_IN_OUT}) {
            assertEquals(0, easing.apply(0), EPSILON);
            assertEquals(1, easing.apply(1), EPSILON);
        }
        assertTrue(Easing.EASE_IN.apply(0.5f) < 0.5f, "ease-in is slow to start");
        assertTrue(Easing.EASE_OUT.apply(0.5f) > 0.5f, "ease-out is fast to start");
        assertEquals(0.5f, Easing.EASE_IN_OUT.apply(0.5f), EPSILON, "ease-in-out is halfway at halfway");
        // CSS's ease at 50% of its time is about 80.2% of the way.
        assertEquals(0.802f, Easing.EASE.apply(0.5f), 0.005f);
    }

    @Test
    void stepsJumpAtTheEndOfEachStep() {
        Easing steps = Easing.steps(4);
        assertEquals(0, steps.apply(0.2f), EPSILON);
        assertEquals(0.25f, steps.apply(0.3f), EPSILON);
        assertEquals(0.75f, steps.apply(0.99f), EPSILON);
        assertEquals(1, steps.apply(1), EPSILON);
    }

    @Test
    void aTransitionEasesToItsTargetAndAChangeMidwayStartsFromWhereItIs() {
        Transition<Float> t = Transition.of(0, 100, Easing.LINEAR);
        assertEquals(0, t.value(0), EPSILON);
        t.set(1f, 1000);
        assertEquals(0, t.value(1000), EPSILON);
        assertEquals(0.5f, t.value(1050), EPSILON);
        assertEquals(1, t.value(1100), EPSILON);
        t.set(0f, 1200);
        t.set(1f, 1250);
        assertEquals(0.5f, t.value(1250), EPSILON, "turned back at halfway, it starts from halfway");
        t.set(1f, 1300);
        assertEquals(0.75f, t.value(1300), EPSILON, "setting the same target doesn't restart it");
    }

    @Test
    void aTransitionWaitsOutItsDelay() {
        Transition<Float> t = new Transition<>(Interpolator.FLOAT, 0f, 100, Easing.LINEAR, 50);
        t.set(1f, 0);
        assertEquals(0, t.value(40), EPSILON);
        assertEquals(0.5f, t.value(100), EPSILON);
    }

    @Test
    void colorsBlendChannelByChannel() {
        assertEquals(0x80808080, (int) Interpolator.ARGB.lerp(0x00000000, 0xffffffff, 0.502f));
        assertEquals(0xff800000, (int) Interpolator.ARGB.lerp(0xffff0000, 0xff000000, 0.5f), "red halves, rounded, and alpha stays");
    }

    @Test
    void keyframesBlendBetweenTheirFramesAndFillMissingEndsWithTheBase() {
        Animation<Float> pulse = Animation.of(Keyframes.floats().at(0.5f, 1f), 100).withEasing(Easing.LINEAR);
        assertEquals(0, pulse.sample(0, 0f), EPSILON, "0% is the base");
        assertEquals(0.5f, pulse.sample(25, 0f), EPSILON);
        assertEquals(1, pulse.sample(50, 0f), EPSILON);
        assertEquals(0.5f, pulse.sample(75, 0f), EPSILON);
    }

    @Test
    void aKeyframesOwnEasingAppliesToTheStretchAfterIt() {
        Animation<Float> a = Animation.of(Keyframes.floats().at(0, 0f, Easing.steps(2)).at(1, 1f), 100).withEasing(Easing.LINEAR);
        assertEquals(0, a.sample(40, 0f), EPSILON);
        assertEquals(0.5f, a.sample(60, 0f), EPSILON);
    }

    @Test
    void outsideItsTimeAnAnimationShowsTheBaseUnlessItFills() {
        Keyframes<Float> rise = Keyframes.floats().at(0, 10f).at(1, 20f);
        Animation<Float> none = Animation.of(rise, 100).withEasing(Easing.LINEAR).withDelay(50);
        assertEquals(0, none.sample(20, 0f), EPSILON, "during the delay, no fill shows the base");
        assertEquals(0, none.sample(200, 0f), EPSILON, "after the end, no fill shows the base");
        Animation<Float> both = none.withFill(Fill.BOTH);
        assertEquals(10, both.sample(20, 0f), EPSILON, "backwards holds the first frame through the delay");
        assertEquals(20, both.sample(200, 0f), EPSILON, "forwards holds the last frame");
        assertTrue(both.finished(150) && !both.finished(149));
    }

    @Test
    void directionsAndIterationsPlayTheWayCssDoes() {
        Keyframes<Float> rise = Keyframes.floats().at(0, 0f).at(1, 1f);
        Animation<Float> base = Animation.of(rise, 100).withEasing(Easing.LINEAR);
        assertEquals(0.75f, base.withDirection(Direction.REVERSE).sample(25, -1f), EPSILON);
        Animation<Float> alternate = base.withIterations(Animation.INFINITE).withDirection(Direction.ALTERNATE);
        assertEquals(0.25f, alternate.sample(25, -1f), EPSILON);
        assertEquals(0.75f, alternate.sample(125, -1f), EPSILON, "the second time it plays backwards");
        assertEquals(0.25f, alternate.sample(225, -1f), EPSILON);
        assertFalse(alternate.finished(1_000_000));
        Animation<Float> twiceForwards = base.withIterations(2).withDirection(Direction.ALTERNATE).withFill(Fill.FORWARDS);
        assertEquals(0, twiceForwards.sample(500, -1f), EPSILON, "two alternating plays end where they began");
        assertEquals(0.5f, base.withIterations(1.5f).withFill(Fill.FORWARDS).sample(500, -1f), EPSILON, "a half iteration ends halfway");
    }

    @Test
    void aRunningAnimationCountsFromWhenItStarted() {
        Animation.Running<Float> running = Animation.of(Keyframes.floats().at(0, 0f).at(1, 1f), 100).withEasing(Easing.LINEAR).start(5000);
        assertEquals(0.5f, running.value(5050, 0f), EPSILON);
    }
}
