package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a wall-buy: a dark oak plate on the wall, sized to the item it sells ({@link WallBuyPlate}), and the item flat on
 * it the way an item frame does. The plate's size comes from drawing the item once into a recorder that keeps only where
 * its vertices land, so it fits whatever the item's renderer draws, a TacZ gun's model included.
 */
public final class WallBuyRenderer implements BlockEntityRenderer<WallBuyBlock.Entity> {
    /** The plate's front, the block's back sixteenth's face, from the block's center toward the wall. */
    private static final float FRONT = 7.0f / 16;
    private static final float BACK = 8.0f / 16;
    /** Just in front of the plate. */
    private static final double DEPTH = FRONT - 0.04;
    private static final float SCALE = 0.8f;
    private static final ResourceLocation PLANKS = ResourceLocation.tryBuild("minecraft", "block/dark_oak_planks");

    /** Each wall-buy's plate, for the item it was measured with. */
    private record Measured(ItemStack stack, WallBuyPlate plate) {}

    private final Map<WallBuyBlock.Entity, Measured> plates = new WeakHashMap<>();
    private final ItemRenderer items;

    public WallBuyRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    @Override
    public void render(WallBuyBlock.Entity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = entity.display();
        Direction facing = entity.getBlockState().getValue(WallBuyBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        drawPlate(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), plate(entity, stack), light, overlay);
        if (!stack.isEmpty()) {
            pose.translate(0, 0, DEPTH);
            pose.scale(SCALE, SCALE, SCALE);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), (int) entity.getBlockPos().asLong());
        }
        pose.popPose();
    }

    private WallBuyPlate plate(WallBuyBlock.Entity entity, ItemStack stack) {
        Measured measured = plates.get(entity);
        if (measured != null && ItemStack.matches(measured.stack(), stack)) {
            return measured.plate();
        }
        if (stack.isEmpty()) {
            plates.put(entity, new Measured(ItemStack.EMPTY, WallBuyPlate.EMPTY));
            return WallBuyPlate.EMPTY;
        }
        Bounds bounds = new Bounds();
        PoseStack measuring = new PoseStack();
        measuring.scale(SCALE, SCALE, SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, 0, OverlayTexture.NO_OVERLAY, measuring, type -> bounds, null, 0);
        if (bounds.isEmpty()) {
            // Nothing drawn yet (a model still loading): try again next frame rather than keep the smallest plate.
            return WallBuyPlate.EMPTY;
        }
        WallBuyPlate plate = WallBuyPlate.around(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY);
        plates.put(entity, new Measured(stack.copy(), plate));
        return plate;
    }

    /** The plate as a box from {@link #FRONT} to {@link #BACK}, its planks laid a block at a time so none stretch. */
    private static void drawPlate(PoseStack pose, VertexConsumer out, WallBuyPlate plate, int light, int overlay) {
        TextureAtlasSprite planks = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLANKS);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        for (double x = plate.minX(); x < plate.maxX() - 1e-6; x += 1) {
            double x1 = Math.min(plate.maxX(), x + 1);
            for (double y = plate.minY(); y < plate.maxY() - 1e-6; y += 1) {
                double y1 = Math.min(plate.maxY(), y + 1);
                float u1 = planks.getU((x1 - x) * 16);
                float v1 = planks.getV((y1 - y) * 16);
                quad(out, matrix, normal, planks.getU0(), planks.getV0(), u1, v1, light, overlay, 0, 0, -1,
                        (float) x, (float) y, FRONT, (float) x1, (float) y, FRONT, (float) x1, (float) y1, FRONT, (float) x, (float) y1, FRONT);
            }
        }
        float x0 = (float) plate.minX();
        float x1 = (float) plate.maxX();
        float y0 = (float) plate.minY();
        float y1 = (float) plate.maxY();
        float edge = planks.getV(1);
        float alongX = planks.getU(Math.min(16, plate.width() * 16));
        float alongY = planks.getU(Math.min(16, plate.height() * 16));
        quad(out, matrix, normal, planks.getU0(), planks.getV0(), alongX, edge, light, overlay, 0, 1, 0,
                x0, y1, FRONT, x1, y1, FRONT, x1, y1, BACK, x0, y1, BACK);
        quad(out, matrix, normal, planks.getU0(), planks.getV0(), alongX, edge, light, overlay, 0, -1, 0,
                x0, y0, BACK, x1, y0, BACK, x1, y0, FRONT, x0, y0, FRONT);
        quad(out, matrix, normal, planks.getU0(), planks.getV0(), alongY, edge, light, overlay, -1, 0, 0,
                x0, y0, BACK, x0, y0, FRONT, x0, y1, FRONT, x0, y1, BACK);
        quad(out, matrix, normal, planks.getU0(), planks.getV0(), alongY, edge, light, overlay, 1, 0, 0,
                x1, y0, FRONT, x1, y0, BACK, x1, y1, BACK, x1, y1, FRONT);
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, Matrix3f normal, float u0, float v0, float u1, float v1, int light, int overlay,
                             float nx, float ny, float nz, float... corners) {
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++) {
            out.vertex(matrix, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
                    .color(255, 255, 255, 255)
                    .uv(uv[i][0], uv[i][1])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normal, nx, ny, nz)
                    .endVertex();
        }
    }

    /** Keeps the smallest box round every vertex drawn into it, across the wall (x and y), and nothing else. */
    private static final class Bounds implements VertexConsumer {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        boolean isEmpty() {
            return minX > maxX;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unsetDefaultColor() {}
    }
}
