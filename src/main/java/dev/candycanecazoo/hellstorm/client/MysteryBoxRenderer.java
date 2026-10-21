package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import java.util.List;

/**
 * The crate's GeckoLib model, a nether portal across the opening under the lid, and the guns: rising out of the portal
 * once the lid is open, flicking from one to the next and slowing down, then the result hanging there for its owner.
 * Locations without the box draw nothing.
 */
public final class MysteryBoxRenderer implements BlockEntityRenderer<MysteryBoxBlock.Entity> {
    private static final float SCALE = 0.5f;
    /** How many guns flick past on the way up. */
    private static final int FLICKS = 30;
    /**
     * TacZ draws a gun in the item frame view with its middle a quarter block below and a tenth behind the point it's drawn
     * at (measured on the AK-47); this centers it.
     */
    private static final float CENTER_X = -0.017f;
    private static final float CENTER_Y = 0.243f;
    private static final float CENTER_Z = 0.109f;
    /**
     * The opening inside the lid's frame and the portal across it, in the model's pixels after {@link MysteryBoxBlock#MODEL_OFFSET}:
     * from the foot's corner, head toward +z. Half a pixel below the top of the frame.
     */
    private static final float PORTAL_X0 = 2;
    private static final float PORTAL_X1 = 14;
    private static final float PORTAL_Z0 = 2;
    private static final float PORTAL_Z1 = 30;
    private static final float PORTAL_Y = 13;
    private static final ResourceLocation PORTAL = new ResourceLocation("minecraft", "block/nether_portal");

    private final ItemRenderer items;
    private final BlockModelRenderer<MysteryBoxBlock.Entity> model = new BlockModelRenderer<>(new Model(), BoneTextures.NONE, MysteryBoxBlock.MODEL_OFFSET);

    public MysteryBoxRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    /** The whole crate is drawn here, not baked into the chunk, so keep it in sight as far as a block would be. */
    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void render(MysteryBoxBlock.Entity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = entity.getLevel();
        if (level == null || !entity.getBlockState().getValue(MysteryBoxBlock.BOX).visible()) {
            return;
        }
        model.render(entity, partialTick, pose, buffers, light, overlay);
        Direction facing = entity.getBlockState().getValue(MysteryBoxBlock.FACING);
        portal(pose, buffers, facing);
        float now = level.getGameTime() + partialTick;
        float elapsed = entity.duration() - (entity.until() - now);
        ItemStack stack;
        double height;
        if (entity.phase() == MysteryBoxBlock.Phase.ROLLING) {
            List<ItemStack> reel = entity.reel();
            if (reel.isEmpty() || elapsed < MysteryBoxBlock.LID_TICKS) {
                return;
            }
            float risen = Mth.clamp((elapsed - MysteryBoxBlock.LID_TICKS) / Math.max(1, entity.duration() - MysteryBoxBlock.LID_TICKS), 0, 1);
            float eased = 1 - (1 - risen) * (1 - risen);
            stack = reel.get((int) (eased * FLICKS) % reel.size());
            height = MysteryBoxBlock.GUN_LOW + (MysteryBoxBlock.GUN_HIGH - MysteryBoxBlock.GUN_LOW) * eased;
        } else if (entity.phase() == MysteryBoxBlock.Phase.OFFERING) {
            stack = entity.result();
            height = MysteryBoxBlock.GUN_HIGH;
        } else {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        Vec3 at = entity.gunAt(height).subtract(Vec3.atLowerCornerOf(entity.getBlockPos()));
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        // Standing up, its length along the crate's, so either long side sees it side on.
        pose.mulPose(Axis.YP.rotationDegrees(facing.getAxis() == Direction.Axis.Z ? 90 : 0));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(CENTER_X, CENTER_Y, CENTER_Z);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LevelRenderer.getLightColor(level, entity.getBlockPos().above()), overlay, pose, buffers,
                level, (int) entity.getBlockPos().asLong());
        pose.popPose();
    }

    /**
     * The portal's animated texture across the opening, a block's worth per tile from the foot's corner, the tiles at the
     * far edges cut to fit. Lit by itself.
     */
    private static void portal(PoseStack pose, MultiBufferSource buffers, Direction facing) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PORTAL);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        Matrix4f matrix = pose.last().pose();
        for (float x = PORTAL_X0; x < PORTAL_X1; x += 16) {
            for (float z = PORTAL_Z0; z < PORTAL_Z1; z += 16) {
                float x1 = Math.min(x + 16, PORTAL_X1);
                float z1 = Math.min(z + 16, PORTAL_Z1);
                float u0 = sprite.getU0();
                float u1 = sprite.getU(x1 - x);
                float v0 = sprite.getV0();
                float v1 = sprite.getV(z1 - z);
                vertex(consumer, matrix, x, z, u0, v0);
                vertex(consumer, matrix, x, z1, u0, v1);
                vertex(consumer, matrix, x1, z1, u1, v1);
                vertex(consumer, matrix, x1, z, u1, v0);
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float z, float u, float v) {
        consumer.vertex(matrix, x / 16, PORTAL_Y / 16, z / 16).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    /**
     * The model, with its open animation fitted to {@link MysteryBoxBlock#LID_TICKS}. The lid closes with the model's
     * {@code box_close} if it has one, or else the open animation played backward.
     */
    private static final class Model extends DefaultedBlockGeoModel<MysteryBoxBlock.Entity> {
        Model() {
            super(Hellstorm.id(MysteryBoxBlock.MODEL));
        }

        @Override
        public Animation getAnimation(MysteryBoxBlock.Entity box, String name) {
            Animation own = super.getAnimation(box, name);
            if (own == null && name.equals(MysteryBoxBlock.Entity.CLOSE)) {
                Animation open = super.getAnimation(box, MysteryBoxBlock.Entity.OPEN);
                own = open == null ? null : Animations.reversed(open, name);
            }
            return own == null ? null : Animations.fitted(own, MysteryBoxBlock.LID_TICKS);
        }
    }
}
