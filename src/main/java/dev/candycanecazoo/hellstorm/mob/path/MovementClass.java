package dev.candycanecazoo.hellstorm.mob.path;

/** A mob's footprint in whole blocks: {@code width} x {@code width} columns, {@code height} tall. */
public record MovementClass(int width, int height) {
    public MovementClass {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Movement class must be at least 1x1, got " + width + "x" + height);
        }
    }

    /** Rounds a bounding box up to whole blocks, so a 0.6 x 1.95 zombie is 1x2. */
    public static MovementClass ofHitbox(float width, float height) {
        return new MovementClass((int) Math.ceil(width), (int) Math.ceil(height));
    }
}
