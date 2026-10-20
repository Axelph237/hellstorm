package dev.candycanecazoo.packapunch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The item in a Pack-a-Punch machine: spinning low inside it while it works, then raised and turning slowly once ready. */
public final class PapMachineRenderer implements BlockEntityRenderer<PapMachineBlock.Entity> {
    private final ItemRenderer items;

    public PapMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(PapMachineBlock.Entity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = machine.displayed();
        if (stack.isEmpty() || machine.getLevel() == null) {
            return;
        }
        float time = machine.getLevel().getGameTime() + partialTick;
        boolean ready = machine.ready();
        pose.pushPose();
        pose.translate(0.5, ready ? 0.95 + 0.04 * Math.sin(time / 8.0) : 0.75, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((time * (ready ? 2.0f : 18.0f)) % 360));
        pose.scale(0.6f, 0.6f, 0.6f);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().above()),
                OverlayTexture.NO_OVERLAY, pose, buffers, machine.getLevel(), (int) machine.getBlockPos().asLong());
        pose.popPose();
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(PapRegistries.PAP_MACHINE_ENTITY.get(), PapMachineRenderer::new);
        }
    }
}
