package dev.candycanecazoo.hellstorm.tacz;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Optional;

/**
 * Lets the client measure a TacZ gun as it's drawn. TacZ's models ignore the buffers they're handed and draw into
 * Minecraft's shared ones, then flush them, so a gun drawn into anything else leaves nothing there. While {@link #into}
 * runs, the mixins on TacZ's models draw the gun's own parts into the given consumer instead, and touch nothing shared.
 * Client only.
 */
public final class GunModelCapture {
    private static final ThreadLocal<VertexConsumer> TARGET = new ThreadLocal<>();

    private GunModelCapture() {}

    public static void into(VertexConsumer target, Runnable draw) {
        VertexConsumer before = TARGET.get();
        TARGET.set(target);
        try {
            draw.run();
        } finally {
            TARGET.set(before);
        }
    }

    public static Optional<VertexConsumer> target() {
        return Optional.ofNullable(TARGET.get());
    }
}
