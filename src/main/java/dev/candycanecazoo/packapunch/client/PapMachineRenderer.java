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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The item in a Pack-a-Punch machine, lying flat along the top of both halves across the seam: the item going in while
 * the fire spreads over it, then the upgraded item waiting to be taken.
 */
public final class PapMachineRenderer implements BlockEntityRenderer<PapMachineBlock.Entity> {
    private static final float SCALE = 1.1f;
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
        Direction facing = machine.getBlockState().getValue(PapMachineBlock.FACING);
        pose.pushPose();
        pose.translate(0.5 + facing.getStepX() * 0.5, PapMachineBlock.TOP + 0.03, 0.5 + facing.getStepZ() * 0.5);
        // As a floor item frame does it: turn the item's front (which faces -z) around, lay it face up, then run its long
        // side (x) along the machine. Face down, a gun's one-sided low-detail model can't be seen from above.
        pose.mulPose(Axis.YP.rotationDegrees(facing.getAxis() == Direction.Axis.Z ? 90 : 0));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
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
