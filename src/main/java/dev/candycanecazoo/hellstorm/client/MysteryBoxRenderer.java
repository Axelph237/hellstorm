package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Above an open box: while rolling, items from the reel flick past, slowing down as the item rises out of the box; then
 * the result hangs there for its owner and sinks back in during the last quarter of the time to take it.
 */
public final class MysteryBoxRenderer implements BlockEntityRenderer<MysteryBoxBlock.Entity> {
    private static final float SCALE = 0.6f;
    private static final double LOW = 0.8;
    private static final double HIGH = 1.3;
    private static final int FLICKS = 40;

    private final ItemRenderer items;

    public MysteryBoxRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    @Override
    public void render(MysteryBoxBlock.Entity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = entity.getLevel();
        if (level == null || entity.duration() <= 0) {
            return;
        }
        float now = level.getGameTime() + partialTick;
        float left = Mth.clamp((entity.until() - now) / entity.duration(), 0, 1);
        ItemStack stack;
        double height;
        if (entity.phase() == MysteryBoxBlock.Phase.ROLLING) {
            List<ItemStack> reel = entity.reel();
            if (reel.isEmpty()) {
                return;
            }
            float eased = 1 - left * left;
            stack = reel.get((int) (eased * FLICKS) % reel.size());
            height = LOW + (HIGH - LOW) * eased;
        } else if (entity.phase() == MysteryBoxBlock.Phase.OFFERING) {
            stack = entity.result();
            height = left < 0.25f ? LOW + (HIGH - LOW) * left / 0.25 : HIGH;
        } else {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, height, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(now * 3 % 360));
        pose.scale(SCALE, SCALE, SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LevelRenderer.getLightColor(level, entity.getBlockPos().above()), overlay, pose, buffers,
                level, (int) entity.getBlockPos().asLong());
        pose.popPose();
    }
}
