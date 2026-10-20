package dev.candycanecazoo.packapunch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.powerup.PowerUpEntity;
import dev.candycanecazoo.packapunch.powerup.PowerUpRegistries;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A power-up's vanilla item, full bright, bobbing and spinning, and blinking out before it goes. */
public final class PowerUpRenderer extends EntityRenderer<PowerUpEntity> {
    private static final float SCALE = 1.1f;

    private final ItemRenderer items;

    public PowerUpRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.2f;
    }

    @Override
    public void render(PowerUpEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!entity.blinkedOut()) {
            float time = entity.tickCount + partialTick;
            pose.pushPose();
            pose.translate(0, 0.15 + Math.sin(time / 8.0) * 0.08, 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * 4));
            pose.scale(SCALE, SCALE, SCALE);
            items.renderStatic(entity.item(), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                    entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PowerUpEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(PowerUpRegistries.POWER_UP.get(), PowerUpRenderer::new);
        }
    }
}
