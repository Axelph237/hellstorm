package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The prompt for what you're looking at, floating at the thing itself and turned to face you. Each client draws its own
 * from its own points, power, and inventory, so the red "can't afford" price is yours alone. Hidden by walls like any
 * block, and it shrinks with distance like a name tag but never below the size it has at {@link #FULL_SIZE_WITHIN}.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class WorldPrompt {
    /** A name tag's scale: one font pixel is 1/40 of a block. */
    private static final float SCALE = 0.025f;
    private static final double FULL_SIZE_WITHIN = 4.0;
    private static final int BACKGROUND = 0x40000000;

    private WorldPrompt() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || mc.options.hideGui
                || !HudState.inGame() || ClientConfig.PROMPT_STYLE.get() != ClientConfig.PromptStyle.WORLD) {
            return;
        }
        PromptOverlay.target(mc).ifPresent(target -> draw(mc, event.getPoseStack(), event.getCamera(), PromptOverlay.text(target.prompt()), target.anchor()));
    }

    private static void draw(Minecraft mc, PoseStack pose, Camera camera, Component text, Vec3 at) {
        Vec3 offset = at.subtract(camera.getPosition());
        float scale = SCALE * (float) Math.max(1.0, offset.length() / FULL_SIZE_WITHIN);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.mulPose(camera.rotation());
        pose.scale(-scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, BACKGROUND, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        pose.popPose();
    }
}
