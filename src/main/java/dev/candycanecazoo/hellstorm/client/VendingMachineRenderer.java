package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The vending machine's GeckoLib model, front on its -z side, lit or unlit, and what it sells on its shelves. The lit
 * texture is a strip of frames played at {@link #LIT_FPS}; while the machine lights up or goes dark, the lit model is
 * faded in over the unlit one. The window's glass is see-through, so it is drawn last, over the shelves.
 */
public final class VendingMachineRenderer implements BlockEntityRenderer<VendingMachineBlock.Entity> {
    private static final ResourceLocation UNLIT = Hellstorm.id("textures/block/" + VendingMachineBlock.MODEL + ".png");
    private static final ResourceLocation LIT = Hellstorm.id("textures/block/" + VendingMachineBlock.MODEL + "_on.png");
    private static final int LIT_FRAMES = 45;
    private static final float LIT_FPS = 15;
    /** The window's glass is the root bone's own cube; the shelves are the cabin's. */
    private static final String GLASS = "machine";
    private static final String SHELVES = "cabin";
    /**
     * Where the items stand, in the model's pixels: over the two dark marks on each shelf's front edge, on the shelves' tops,
     * three deep from the front (-z) back.
     */
    private static final float[] SPOTS_X = {3.5f, 9.5f};
    private static final float[] SHELF_TOPS = {12, 18, 24};
    private static final float[] ROWS_Z = {-6, -4, -2};
    private static final float ITEM_SCALE = 0.28f;

    private final ItemRenderer items;
    private final Model model = new Model();

    public VendingMachineRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    /** The whole machine is drawn here, not baked into the chunk, so keep it in sight as far as a block would be. */
    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void render(VendingMachineBlock.Entity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        float lit = machine.lit(level.getGameTime(), partialTick);
        int frame = (int) ((long) ((level.getGameTime() + (double) partialTick) / 20 * LIT_FPS) % LIT_FRAMES);
        ResourceLocation texture = lit >= 1 ? LIT : UNLIT;
        model.begin(texture, frame);
        model.render(machine, partialTick, pose, buffers, light, overlay);
        if (model.shelves != null) {
            stock(machine, model.shelves.stack(), buffers, light, overlay);
        }
        if (lit > 0 && lit < 1) {
            model.fadeIn(machine, pose, buffers, partialTick, light, overlay, lit, frame);
        }
        model.draw(texture, frame);
        model.glass(buffers, light, overlay);
    }

    /** Three of what the machine sells, one behind another, at each of the shelves' six spots, upright and facing out. */
    private void stock(VendingMachineBlock.Entity machine, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = machine.display();
        if (stack.isEmpty()) {
            return;
        }
        int seed = (int) machine.getBlockPos().asLong();
        float lift = ITEM_SCALE / 2;
        for (float top : SHELF_TOPS) {
            for (float x : SPOTS_X) {
                for (float z : ROWS_Z) {
                    pose.pushPose();
                    pose.translate(x / 16, top / 16 + lift, z / 16);
                    pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                    items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, machine.getLevel(), seed);
                    pose.popPose();
                }
            }
        }
    }

    /** A bone's own cubes and the pose they were drawn in. */
    private record Placed(GeoBone bone, Matrix4f pose, Matrix3f normal) {
        PoseStack stack() {
            PoseStack stack = new PoseStack();
            stack.last().pose().set(pose);
            stack.last().normal().set(normal);
            return stack;
        }
    }

    private static final class Model extends GeoBlockRenderer<VendingMachineBlock.Entity> {
        /** The texture being drawn, and the frame of the lit strip, or -1 when the texture isn't the lit one. */
        private ResourceLocation texture = UNLIT;
        private int stripFrame = -1;
        /** Where the glass and the shelves were drawn this frame, to draw the glass and the stock over them afterward. */
        private @Nullable Placed glass;
        private @Nullable Placed shelves;
        private boolean fadingIn;

        Model() {
            super(new DefaultedBlockGeoModel<>(Hellstorm.id(VendingMachineBlock.MODEL)));
        }

        /** Starts a frame: nothing placed yet. */
        void begin(ResourceLocation texture, int frame) {
            glass = null;
            shelves = null;
            draw(texture, frame);
        }

        void draw(ResourceLocation texture, int frame) {
            this.texture = texture;
            stripFrame = texture == LIT ? frame : -1;
        }

        /** The lit model again over the unlit one just drawn, see-through by {@code 1 - lit}, in the same pose. */
        void fadeIn(VendingMachineBlock.Entity machine, PoseStack pose, MultiBufferSource buffers, float partialTick, int light, int overlay,
                    float lit, int frame) {
            draw(LIT, frame);
            RenderType type = RenderType.entityTranslucent(LIT);
            BakedGeoModel baked = getGeoModel().getBakedModel(getGeoModel().getModelResource(machine));
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            rotateBlock(getFacing(machine), pose);
            fadingIn = true;
            animatable = machine;
            reRender(baked, pose, buffers, machine, type, buffers.getBuffer(type), partialTick, light, overlay, 1, 1, 1, lit);
            animatable = null;
            fadingIn = false;
            pose.popPose();
        }

        /** The glass held back this frame, see-through, over everything behind it. */
        void glass(MultiBufferSource buffers, int light, int overlay) {
            if (glass == null) {
                return;
            }
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(texture));
            PoseStack at = glass.stack();
            for (GeoCube cube : glass.bone().getCubes()) {
                at.pushPose();
                renderCube(at, cube, buffer, light, overlay, 1, 1, 1, 1);
                at.popPose();
            }
        }

        @Override
        public ResourceLocation getTextureLocation(VendingMachineBlock.Entity machine) {
            return texture;
        }

        @Override
        public RenderType getRenderType(VendingMachineBlock.Entity machine, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
            return RenderType.entityCutoutNoCull(texture);
        }

        @Override
        protected void rotateBlock(Direction facing, PoseStack pose) {
            BlockModelRenderer.turn(pose, facing, Direction.NORTH, VendingMachineBlock.MODEL_OFFSET);
        }

        /** Holds back the glass, and notes where it and the shelves are, to draw over them once the rest is drawn. */
        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                      float red, float green, float blue, float alpha) {
            boolean isGlass = bone.getName().equals(GLASS);
            if (!fadingIn && (isGlass || bone.getName().equals(SHELVES))) {
                Placed placed = new Placed(bone, new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()));
                if (isGlass) {
                    glass = placed;
                } else {
                    shelves = placed;
                }
            }
            if (!isGlass) {
                super.renderCubesOfBone(pose, bone, buffer, light, overlay, red, green, blue, alpha);
            }
        }

        /** On the lit strip, the model's texture coordinates, which span one frame, are moved to this frame's. */
        @Override
        public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer, int light, int overlay,
                                         float red, float green, float blue, float alpha) {
            if (stripFrame < 0) {
                super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, red, green, blue, alpha);
                return;
            }
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f at = pose.transformPosition(new Vector3f(vertex.position()));
                buffer.vertex(at.x(), at.y(), at.z(), red, green, blue, alpha, vertex.texU(), (stripFrame + vertex.texV()) / LIT_FRAMES,
                        overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
