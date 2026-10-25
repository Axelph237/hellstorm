package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Embers rising behind a screen, in GUI pixels. They drift up from the bottom edge and off any rectangle given to
 * {@link #emitFrom}, scatter away from the cursor and follow its sweep, and burst where the player clicks. Ticked 20
 * times a second and smoothed between ticks; drawn additively in one draw call.
 */
public final class Embers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation DOT = Hellstorm.id("dynamic/ember");
    private static final int DOT_SIZE = 16;
    private static final int MAX = 700;
    private static final float CURSOR_RADIUS = 28;
    private static final float TIMING_SECONDS = 5;

    private static boolean dotRegistered;

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
        float x, y, prevX, prevY, vx, vy, size, phase;
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
        for (int i = 0; i < Math.max(1, width / 80); i++) {
            spawn(random.nextFloat() * width, height + 2, 0, -0.6f, 1);
        }
        embers.removeIf(e -> ++e.age >= e.life || e.y < -8);
        for (Ember e : embers) {
            e.prevX = e.x;
            e.prevY = e.y;
            e.vy -= 0.045f;
            e.vx += Mth.sin(e.age * 0.25f + e.phase) * 0.06f;
            float dx = e.x - mouseX;
            float dy = e.y - mouseY;
            float distance = Mth.sqrt(dx * dx + dy * dy);
            if (distance < CURSOR_RADIUS && distance > 0.01f) {
                float push = 1 - distance / CURSOR_RADIUS;
                e.vx += dx / distance * push * 1.4f + sweepX * push * 0.25f;
                e.vy += dy / distance * push * 1.4f + sweepY * push * 0.25f;
            }
            e.vx *= 0.92f;
            e.vy *= 0.94f;
            e.x += e.vx;
            e.y += e.vy;
        }
        tickNanos += System.nanoTime() - start;
        ticks++;
    }

    /** Sheds embers off the top edge of a rectangle this tick, as a hovered button does. */
    public void emitFrom(int x0, int x1, int y, int count) {
        for (int i = 0; i < count; i++) {
            spawn(x0 + random.nextFloat() * (x1 - x0), y, 0, -0.4f, 0.7f);
        }
    }

    public void burst(double x, double y) {
        for (int i = 0; i < 40; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float speed = 1 + random.nextFloat() * 3.5f;
            spawn((float) x, (float) y, Mth.cos(angle) * speed, Mth.sin(angle) * speed - 1, 0.8f);
        }
    }

    private void spawn(float x, float y, float vx, float vy, float lifeScale) {
        if (embers.size() >= MAX) {
            return;
        }
        Ember e = new Ember();
        e.x = e.prevX = x;
        e.y = e.prevY = y;
        e.vx = vx + (random.nextFloat() - 0.5f) * 0.6f;
        e.vy = vy - random.nextFloat() * 0.5f;
        e.size = 1.5f + random.nextFloat() * 2.5f;
        e.phase = random.nextFloat() * Mth.TWO_PI;
        e.life = (int) ((40 + random.nextInt(50)) * lifeScale);
        embers.add(e);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long start = System.nanoTime();
        graphics.fillGradient(0, height - height / 4, width, height, 0x00000000, 0x55C02000);
        graphics.flush();
        if (!embers.isEmpty()) {
            draw(graphics.pose().last().pose(), partialTick);
        }
        drawNanos += System.nanoTime() - start;
        frames++;
        logTiming();
    }

    private void draw(Matrix4f pose, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, dot());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
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
            buffer.vertex(pose, x - half, y - half, 0).color(red, green, blue, alpha).uv(0, 0).endVertex();
            buffer.vertex(pose, x - half, y + half, 0).color(red, green, blue, alpha).uv(0, 1).endVertex();
            buffer.vertex(pose, x + half, y + half, 0).color(red, green, blue, alpha).uv(1, 1).endVertex();
            buffer.vertex(pose, x + half, y - half, 0).color(red, green, blue, alpha).uv(1, 0).endVertex();
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

    /** A soft white dot, made in memory and drawn tinted. */
    private static ResourceLocation dot() {
        if (!dotRegistered) {
            NativeImage image = new NativeImage(DOT_SIZE, DOT_SIZE, false);
            float centre = (DOT_SIZE - 1) / 2f;
            for (int y = 0; y < DOT_SIZE; y++) {
                for (int x = 0; x < DOT_SIZE; x++) {
                    float r = Mth.sqrt((x - centre) * (x - centre) + (y - centre) * (y - centre)) / (DOT_SIZE / 2f);
                    float falloff = Mth.clamp(1 - r, 0, 1);
                    int alpha = (int) (falloff * falloff * 255);
                    image.setPixelRGBA(x, y, alpha << 24 | 0xFFFFFF);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(DOT, texture);
            dotRegistered = true;
        }
        return DOT;
    }
}
