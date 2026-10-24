package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.mob.MobScale;
import dev.candycanecazoo.hellstorm.net.ScalePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;

/**
 * Draws scaled mobs ({@link MobScale}) scaled about their feet, whatever renderer draws them: Forge's living-entity
 * render event for most, GeckoLib's own for GeckoLib mobs, which don't fire Forge's. The shadow keeps its size.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class MobScales {
    private MobScales() {}

    public static void apply(ScalePacket packet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(packet.entity());
        if (entity != null) {
            MobScale.set(entity, packet.scale());
        }
    }

    /**
     * Last, so the pose is only pushed when the render goes ahead: a cancelled Pre gets no Post to pop it. The renderer's
     * caller draws the shadow and the debug hitbox after this, so the scale must be popped, not left on the stack.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void scaleLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        float scale = MobScale.of(event.getEntity());
        if (scale != 1f) {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.scale(scale, scale, scale);
        }
    }

    @SubscribeEvent
    public static void scaleLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (MobScale.of(event.getEntity()) != 1f) {
            event.getPoseStack().popPose();
        }
    }

    /** GeckoLib pushes the pose before its Pre and pops it after its Post, so the scale needs no pop of its own. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void scaleGeckoPre(GeoRenderEvent.Entity.Pre event) {
        float scale = MobScale.of(event.getEntity());
        if (scale != 1f) {
            event.getPoseStack().scale(scale, scale, scale);
        }
    }
}
