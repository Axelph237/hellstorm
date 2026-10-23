package dev.candycanecazoo.hellstorm.mob;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where a bullet hit a standing zombie, told from the hit point the way TacZ tells a headshot. The zombie's model is
 * 32 pixels tall: legs to pixel 12, body and arms to 24, the head above. Across, its body is 8 pixels of the model's 16,
 * the arms the 4 on each side; the hitbox is narrower than the model, so its width is scaled to the model's.
 */
public enum BodyPart {
    HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;

    private static final double LEGS_TOP = 12 / 32.0;
    private static final double BODY_TOP = 24 / 32.0;
    /** Half the body's width, as a share of half the model's: 4 pixels of 8. */
    private static final double BODY_HALF = 0.5;

    /** The bit this limb sets in {@link Limbs}' removed mask, or 0 for the head and body, which don't come off. */
    public int bit() {
        return switch (this) {
            case LEFT_ARM -> 1;
            case RIGHT_ARM -> 2;
            case LEFT_LEG -> 4;
            case RIGHT_LEG -> 8;
            default -> 0;
        };
    }

    public boolean leg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }

    /**
     * The part {@code hit} is on, for a zombie standing at {@code feet}, {@code width} wide and {@code height} tall, facing
     * {@code bodyYaw} degrees. A TacZ headshot is the head whatever the point says.
     */
    public static BodyPart at(Vec3 hit, Vec3 feet, double width, double height, float bodyYaw, boolean headshot) {
        double up = (hit.y - feet.y) / height;
        if (headshot || up >= BODY_TOP) {
            return HEAD;
        }
        // The zombie's right, in the world: facing +z at yaw 0, its right is -x.
        double yaw = bodyYaw * Mth.DEG_TO_RAD;
        double right = (hit.x - feet.x) * -Math.cos(yaw) + (hit.z - feet.z) * -Math.sin(yaw);
        double across = right / (width / 2);
        if (up < LEGS_TOP) {
            return across >= 0 ? RIGHT_LEG : LEFT_LEG;
        }
        if (Math.abs(across) < BODY_HALF) {
            return BODY;
        }
        return across > 0 ? RIGHT_ARM : LEFT_ARM;
    }
}
