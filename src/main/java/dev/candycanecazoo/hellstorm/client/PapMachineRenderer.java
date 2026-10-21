package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.feature.TwoBlockMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * The machine's GeckoLib model, and the item in it, lying flat along the top of both halves across the seam: the item
 * going in while it burns, engulfed in vanilla's fire sprites, then the upgraded item waiting to be taken.
 */
public final class PapMachineRenderer implements BlockEntityRenderer<PapMachineBlock.Entity> {
    /**
     * TacZ draws a gun about three blocks long in the item frame view, with its middle a quarter block below and a tenth
     * behind the point it's drawn at (measured on the AK-47). This brings it to under two blocks, centered across the table.
     */
    private static final float SCALE = 0.58f;
    private static final float CENTER_X = -0.017f;
    private static final float CENTER_Y = 0.243f;
    /** Flames rise to full height over this many ticks: the fire catching and engulfing the item. */
    private static final float GROW_TICKS = 10;
    private static final int FLAME_COLUMNS = 5;
    private static final float FLAME_HEIGHT = 0.75f;
    private final ItemRenderer items;
    private final BlockModelRenderer<PapMachineBlock.Entity> model = new BlockModelRenderer<>(
            new DefaultedBlockGeoModel<>(Hellstorm.id(PapMachineBlock.MODEL)), BoneTextures.HELLFIRE_UPGRADER, Vec3.ZERO);

    public PapMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    /** The whole table is drawn here, not baked into the chunk, so keep it in sight as far as a block would be. */
    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void render(PapMachineBlock.Entity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        model.render(machine, partialTick, pose, buffers, light, overlay);
        ItemStack stack = machine.displayed();
        if (stack.isEmpty() || machine.getLevel() == null) {
            return;
        }
        Direction facing = TwoBlockMachine.along(machine.getBlockState());
        Direction side = facing.getCounterClockWise();
        pose.pushPose();
        pose.translate(0.5 + facing.getStepX() * 0.5 + side.getStepX() * PapMachineBlock.SIDE_OFFSET, PapMachineBlock.TOP + 0.01,
                0.5 + facing.getStepZ() * 0.5 + side.getStepZ() * PapMachineBlock.SIDE_OFFSET);
        // As a floor item frame does it: turn the item's front (which faces -z) around, lay it face up, then run its long
        // side (x) along the machine.
        pose.mulPose(Axis.YP.rotationDegrees(facing.getAxis() == Direction.Axis.Z ? 90 : 0));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        // Laid face up, its back already rests on the table; center its length, and its height in the frame, which now runs across the table.
        pose.translate(CENTER_X, CENTER_Y, 0);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().above()),
                OverlayTexture.NO_OVERLAY, pose, buffers, machine.getLevel(), (int) machine.getBlockPos().asLong());
        pose.popPose();
        long burning = machine.burning(machine.getLevel().getGameTime());
        if (burning >= 0) {
            flames(machine, pose, buffers, burning + partialTick);
        }
    }

    /**
     * Columns of vanilla fire along the item, turned to face the camera and lit by themselves, as a burning entity's are.
     * They grow from the table over {@link #GROW_TICKS} and flicker in height.
     */
    private static void flames(PapMachineBlock.Entity machine, PoseStack pose, MultiBufferSource buffers, float ticks) {
        TextureAtlasSprite[] sprites = {ModelBakery.FIRE_0.sprite(), ModelBakery.FIRE_1.sprite()};
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        float yaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        float grown = Math.min(1f, ticks / GROW_TICKS);
        float width = (float) (2 * PapMachineBlock.ITEM_HALF_LENGTH / FLAME_COLUMNS * 1.5);
        Vec3 origin = Vec3.atLowerCornerOf(machine.getBlockPos());
        for (int i = 0; i < FLAME_COLUMNS; i++) {
            Vec3 at = machine.along(-1 + (2 * i + 1) / (double) FLAME_COLUMNS).subtract(origin);
            float height = FLAME_HEIGHT * grown * (0.85f + 0.15f * Mth.sin(ticks * 0.35f + i * 1.7f));
            TextureAtlasSprite sprite = sprites[i % 2];
            pose.pushPose();
            pose.translate(at.x, PapMachineBlock.TOP - 0.02, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            Matrix4f matrix = pose.last().pose();
            float half = width / 2;
            // Both windings, so the flame shows whichever way the quad's cull faces.
            quad(consumer, matrix, sprite, -half, half, height);
            quad(consumer, matrix, sprite, half, -half, height);
            pose.popPose();
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, TextureAtlasSprite sprite, float left, float right, float height) {
        vertex(consumer, matrix, left, 0, sprite.getU0(), sprite.getV1());
        vertex(consumer, matrix, right, 0, sprite.getU1(), sprite.getV1());
        vertex(consumer, matrix, right, height, sprite.getU1(), sprite.getV0());
        vertex(consumer, matrix, left, height, sprite.getU0(), sprite.getV0());
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v) {
        consumer.vertex(matrix, x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }
}
