package dev.candycanecazoo.hellstorm.mob;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyPartTest {
    private static final Vec3 FEET = new Vec3(10, 64, 10);
    private static final double WIDTH = 0.6;
    private static final double HEIGHT = 1.95;

    /** A point {@code right} blocks to the zombie's right and {@code up} blocks over its feet, facing {@code yaw}. */
    private static BodyPart at(double right, double up, float yaw) {
        double rad = Math.toRadians(yaw);
        Vec3 toRight = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        return BodyPart.at(FEET.add(toRight.scale(right)).add(0, up, 0), FEET, WIDTH, HEIGHT, yaw, false);
    }

    @Test
    void legsArmsBodyAndHeadByHeightAndSideWhicheverWayItFaces() {
        for (float yaw : new float[] {0, 90, 180, -135}) {
            assertEquals(BodyPart.RIGHT_LEG, at(0.1, 0.3, yaw), "yaw " + yaw);
            assertEquals(BodyPart.LEFT_LEG, at(-0.1, 0.3, yaw), "yaw " + yaw);
            assertEquals(BodyPart.RIGHT_ARM, at(0.25, 1.1, yaw), "yaw " + yaw);
            assertEquals(BodyPart.LEFT_ARM, at(-0.25, 1.1, yaw), "yaw " + yaw);
            assertEquals(BodyPart.BODY, at(0.05, 1.1, yaw), "yaw " + yaw);
            assertEquals(BodyPart.HEAD, at(0, 1.7, yaw), "yaw " + yaw);
        }
    }

    @Test
    void facingSouthItsRightIsWest() {
        assertEquals(BodyPart.RIGHT_ARM, BodyPart.at(FEET.add(-0.29, 1.1, 0), FEET, WIDTH, HEIGHT, 0, false));
    }

    @Test
    void aTaczHeadshotIsTheHeadWhereverThePointIs() {
        assertEquals(BodyPart.HEAD, BodyPart.at(FEET.add(0, 0.3, 0), FEET, WIDTH, HEIGHT, 0, true));
    }

    @Test
    void onlyLimbsHaveBitsAndEachItsOwn() {
        assertEquals(0, BodyPart.HEAD.bit() | BodyPart.BODY.bit());
        assertEquals(15, BodyPart.LEFT_ARM.bit() | BodyPart.RIGHT_ARM.bit() | BodyPart.LEFT_LEG.bit() | BodyPart.RIGHT_LEG.bit());
    }
}
