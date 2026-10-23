package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.mob.BodyPart;
import dev.candycanecazoo.hellstorm.mob.Limbs;
import dev.candycanecazoo.hellstorm.net.LimbsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws zombies without the limbs they've lost, and crawlers lying prone, pulling themselves along by their arms. It
 * replaces vanilla's zombie renderer, which it otherwise matches.
 */
public final class ZombieLimbs {
    private ZombieLimbs() {}

    /** A zombie's limbs and crawl, from the server, kept in the client's copy of its NBT. */
    public static void apply(LimbsPacket packet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(packet.entity());
        if (entity == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        data.putByte(Limbs.REMOVED, packet.removed());
        if (packet.crawlSpeed() > 0) {
            CompoundTag crawler = new CompoundTag();
            crawler.putFloat(Limbs.SPEED, packet.crawlSpeed());
            data.put(Limbs.CRAWLER, crawler);
        } else {
            data.remove(Limbs.CRAWLER);
        }
        entity.refreshDimensions();
    }

    static final class Model extends ZombieModel<Zombie> {
        Model(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(Zombie zombie, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(zombie, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            // One model draws every zombie, so each part is shown or hidden again every time.
            leftArm.visible = !Limbs.lost(zombie, BodyPart.LEFT_ARM);
            rightArm.visible = !Limbs.lost(zombie, BodyPart.RIGHT_ARM);
            leftLeg.visible = !Limbs.lost(zombie, BodyPart.LEFT_LEG);
            rightLeg.visible = !Limbs.lost(zombie, BodyPart.RIGHT_LEG);
            if (Limbs.crawls(zombie)) {
                // Lying prone, "up" the body is ahead: the arms reach out past the head in turn, and the legs drag.
                float reach = 0.6f * Mth.sin(limbSwing * 0.6f) * Math.min(1, limbSwingAmount * 2);
                rightArm.xRot = -Mth.PI + reach;
                leftArm.xRot = -Mth.PI - reach;
                rightArm.yRot = leftArm.yRot = 0;
                rightArm.zRot = 0.15f;
                leftArm.zRot = -0.15f;
                rightLeg.xRot = 0.1f * Mth.cos(limbSwing * 0.6f);
                leftLeg.xRot = -0.1f * Mth.cos(limbSwing * 0.6f);
                head.xRot = -Mth.PI / 4;
                hat.copyFrom(head);
            }
        }
    }

    static final class Renderer extends AbstractZombieRenderer<Zombie, Model> {
        Renderer(EntityRendererProvider.Context context) {
            super(context, new Model(context.bakeLayer(ModelLayers.ZOMBIE)), new Model(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new Model(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        }

        /** A crawler is laid face down along its heading, as vanilla lays a crawling player. */
        @Override
        protected void setupRotations(Zombie zombie, PoseStack pose, float ageInTicks, float yaw, float partialTicks) {
            super.setupRotations(zombie, pose, ageInTicks, yaw, partialTicks);
            if (Limbs.crawls(zombie) && zombie.deathTime == 0) {
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.translate(0.0f, -1.0f, 0.3f);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void registerZombieRenderer(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.ZOMBIE, Renderer::new);
        }
    }
}
