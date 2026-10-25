package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Square embers streaming out of a screen's bottom right corner, in GUI pixels: a dense band flowing up and left, at
 * 135 degrees, fading before it reaches the middle. They scatter away from the cursor and follow its sweep. Ticked 20
 * times a second and smoothed between ticks; drawn additively in one draw call.
 */
public final class Embers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX = 1200;
    private static final int PER_TICK = 14;
    private static final float CURSOR_RADIUS = 28;
    private static final float TIMING_SECONDS = 5;
    /** Across the stream, measured from its centre line through the corner. */
    private static final float BAND = 34;
    private static final float SPEED = 1.6f;
    private static final float FLOW_X = -Mth.SQRT_OF_TWO / 2;
    private static final float FLOW_Y = -Mth.SQRT_OF_TWO / 2;
    private static final float ACROSS_X = Mth.SQRT_OF_TWO / 2;
    private static final float ACROSS_Y = -Mth.SQRT_OF_TWO / 2;

    private final RandomSource random = RandomSource.create();
    private final List<Ember> embers = new ArrayList<>();
    private int width;
    private int height;
    private float mouseX = -1000;
    private float mouseY = -1000;
    private float lastMouseX = -1000;
    private float lastMouseY = -1000;

    private long tickNanos;
    private long drawNanos;
    private int frames;
    private int ticks;
    private long timingStart = System.nanoTime();

    private static final class Ember {
        float x, y, prevX, prevY, vx, vy, speed, size, phase;
        int age, life;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void tick() {
        long start = System.nanoTime();
        float sweepX = mouseX - lastMouseX;
        float sweepY = mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        for (int i = 0; i < PER_TICK; i++) {
            spawn();
        }
        embers.removeIf(e -> ++e.age >= e.life);
        for (Ember e : embers) {
            e.prevX = e.x;
            e.prevY = e.y;
            float sway = Mth.sin(e.age * 0.3f + e.phase) * 0.25f;
            e.vx += (FLOW_X * e.speed + ACROSS_X * sway - e.vx) * 0.12f;
            e.vy += (FLOW_Y * e.speed + ACROSS_Y * sway - e.vy) * 0.12f;
            float dx = e.x - mouseX;
            float dy = e.y - mouseY;
            float distance = Mth.sqrt(dx * dx + dy * dy);
            if (distance < CURSOR_RADIUS && distance > 0.01f) {
                float push = 1 - distance / CURSOR_RADIUS;
                e.vx += dx / distance * push * 1.4f + sweepX * push * 0.25f;
                e.vy += dy / distance * push * 1.4f + sweepY * push * 0.25f;
            }
            e.x += e.vx;
            e.y += e.vy;
        }
        tickNanos += System.nanoTime() - start;
        ticks++;
    }

    /** Just past the corner, somewhere across the band, so the stream enters already moving. */
    private void spawn() {
        if (embers.size() >= MAX) {
            return;
        }
        Ember e = new Ember();
        // Two randoms summed crowd the embers toward the band's centre line.
        float across = (random.nextFloat() + random.nextFloat() - 1) * BAND;
        e.x = e.prevX = width + 4 + ACROSS_X * across;
        e.y = e.prevY = height + 4 + ACROSS_Y * across;
        e.speed = SPEED * (0.7f + random.nextFloat() * 0.6f);
        e.vx = FLOW_X * e.speed;
        e.vy = FLOW_Y * e.speed;
        e.size = 0.75f + random.nextFloat() * 1.25f;
        e.phase = random.nextFloat() * Mth.TWO_PI;
        e.life = 50 + random.nextInt(50) - (int) Math.abs(across);
        embers.add(e);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long start = System.nanoTime();
        graphics.flush();
        if (!embers.isEmpty()) {
            draw(graphics.pose().last().pose(), partialTick);
        }
        drawNanos += System.nanoTime() - start;
        frames++;
        logTiming();
    }

    private void draw(Matrix4f pose, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Ember e : embers) {
            float t = (e.age + partialTick) / e.life;
            float x = Mth.lerp(partialTick, e.prevX, e.x);
            float y = Mth.lerp(partialTick, e.prevY, e.y);
            float half = e.size * (1 - t * 0.6f);
            // White-hot, then yellow, orange and a dull red as it cools and fades.
            float red = 1;
            float green = Mth.clamp(1 - t * 1.6f, 0.1f, 1);
            float blue = Mth.clamp(0.7f - t * 3, 0, 1);
            float alpha = Mth.clamp((1 - t) * 1.4f, 0, 1) * Mth.clamp(e.age / 3f, 0, 1);
            buffer.vertex(pose, x - half, y - half, 0).color(red, green, blue, alpha).endVertex();
            buffer.vertex(pose, x - half, y + half, 0).color(red, green, blue, alpha).endVertex();
            buffer.vertex(pose, x + half, y + half, 0).color(red, green, blue, alpha).endVertex();
            buffer.vertex(pose, x + half, y - half, 0).color(red, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** CPU time only: the GPU draws later, and F3's frame graph shows the whole frame. */
    private void logTiming() {
        long now = System.nanoTime();
        if (now - timingStart < TIMING_SECONDS * 1e9) {
            return;
        }
        LOGGER.info("Embers: {} alive, tick {} us, draw {} us a frame ({} fps)", embers.size(),
                ticks == 0 ? 0 : tickNanos / ticks / 1000, frames == 0 ? 0 : drawNanos / frames / 1000,
                Math.round(frames / ((now - timingStart) / 1e9)));
        tickNanos = drawNanos = 0;
        ticks = frames = 0;
        timingStart = now;
    }
}
