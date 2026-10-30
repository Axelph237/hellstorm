package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.tacz.GunModelCapture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Draws a wall-buy: the user's frame on the wall ({@link WallBuyFrame}), as long as the item it sells needs
 * ({@link WallBuyPlate}) and faced like the block it's on, and the item flat on it the way an item frame does. The plate's size comes from drawing the item once into a
 * recorder that keeps only where its vertices land, so it fits whatever the item's renderer draws. A TacZ gun draws into
 * Minecraft's shared buffers, not the ones it's handed, so its body is drawn into the recorder through
 * {@link GunModelCapture}. Within {@link #GLOW_DISTANCE} of the player the item glows: it's drawn lit, over a soft pulsing
 * halo. Both are depth-tested like anything else, so a wall in between hides them.
 */
public final class WallBuyRenderer implements BlockEntityRenderer<WallBuyBlock.Entity> {
    /** The frame's recess, a sixteenth out from the wall; its rim stands a sixteenth further. From the block's center toward the wall. */
    private static final float FRONT = 7.0f / 16;
    /** The wall. */
    private static final float BACK = 8.0f / 16;
    /** Just in front of the plate. */
    private static final double DEPTH = FRONT - 0.04;
    private static final float SCALE = 0.8f;
    /** The frame model, which the plate is drawn from. */
    private static final ResourceLocation FRAME = Hellstorm.id("shapes/wall_buy.json");
    /** The plate when there's no block behind it to look like. */
    private static final ResourceLocation PLANKS = ResourceLocation.tryBuild("minecraft", "block/dark_oak_planks");
    /** The glow is full within this many blocks less {@link #GLOW_FADE}, and gone past it. */
    private static final double GLOW_DISTANCE = 5;
    private static final double GLOW_FADE = 1.5;
    /** How far the halo reaches past the item, in blocks. */
    private static final double HALO_REACH = 0.2;
    private static final int HALO_COLOR = 0xFFD24A;
    /** The halo, just in front of the plate and behind the item. */
    private static final float HALO_Z = FRONT - 0.01f;

    /** Each wall-buy's plate, and where its item draws, for the item it was measured with. */
    private record Measured(ItemStack stack, WallBuyPlate plate, Optional<WallBuyPlate> item) {}

    /** One layer of the plate's face: a sprite and its tint. */
    private record Skin(TextureAtlasSprite sprite, int color) {}

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
        // Measure before taking a buffer: drawing an item can flush the buffer a renderer is holding.
        Measured measured = measure(entity, stack);
        List<Skin> skins = skins(entity, facing);
        VertexConsumer plateOut = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (Skin skin : skins) {
            drawPlate(pose, plateOut, measured.plate(), skin, light, overlay);
        }
        if (!stack.isEmpty()) {
            float glow = glow(entity, partialTick);
            if (glow > 0 && measured.item().isPresent()) {
                drawHalo(pose, buffers.getBuffer(RenderType.eyes(GlowTexture.get())), measured.item().get(), glow);
            }
            pose.translate(0, 0, DEPTH);
            pose.scale(SCALE, SCALE, SCALE);
            items.renderStatic(stack, ItemDisplayContext.FIXED, lit(light, glow), overlay, pose, buffers, entity.getLevel(), (int) entity.getBlockPos().asLong());
        }
        pose.popPose();
    }

    private Measured measure(WallBuyBlock.Entity entity, ItemStack stack) {
        Measured measured = plates.get(entity);
        if (measured != null && ItemStack.matches(measured.stack(), stack)) {
            return measured;
        }
        if (stack.isEmpty()) {
            Measured empty = new Measured(ItemStack.EMPTY, WallBuyPlate.EMPTY, Optional.empty());
            plates.put(entity, empty);
            return empty;
        }
        Bounds bounds = new Bounds();
        PoseStack measuring = new PoseStack();
        measuring.scale(SCALE, SCALE, SCALE);
        GunModelCapture.into(bounds, () -> items.renderStatic(stack, ItemDisplayContext.FIXED, 0, OverlayTexture.NO_OVERLAY, measuring, type -> bounds, null, 0));
        if (bounds.isEmpty()) {
            // Nothing drawn yet (a model still loading): try again next frame rather than keep the smallest plate.
            return new Measured(stack, WallBuyPlate.EMPTY, Optional.empty());
        }
        Measured fresh = new Measured(stack.copy(), WallBuyPlate.around(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY),
                Optional.of(new WallBuyPlate(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)));
        plates.put(entity, fresh);
        return fresh;
    }

    /**
     * The plate's face, layer by layer: the face of the block behind the wall-buy that looks out at it, each layer with its
     * tint (grass, leaves); that block's particle texture if it has no such face; dark oak with nothing there.
     */
    private static List<Skin> skins(WallBuyBlock.Entity entity, Direction facing) {
        TextureAtlasSprite planks = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLANKS);
        Level level = entity.getLevel();
        if (level == null) {
            return List.of(new Skin(planks, 0xffffff));
        }
        BlockPos behind = entity.getBlockPos().relative(facing.getOpposite());
        BlockState state = level.getBlockState(behind);
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return List.of(new Skin(planks, 0xffffff));
        }
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, facing, RandomSource.create(behind.asLong()));
        if (quads.isEmpty()) {
            return List.of(new Skin(model.getParticleIcon(), 0xffffff));
        }
        List<Skin> skins = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            int color = quad.isTinted() ? Minecraft.getInstance().getBlockColors().getColor(state, level, behind, quad.getTintIndex()) : 0xffffff;
            skins.add(new Skin(quad.getSprite(), color));
        }
        return skins;
    }

    /** 0 to 1: how strongly the item glows for this player, full up close and fading out toward {@link #GLOW_DISTANCE}. */
    private static float glow(WallBuyBlock.Entity entity, float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        double distance = Math.sqrt(player.getEyePosition(partialTick).distanceToSqr(Vec3.atCenterOf(entity.getBlockPos())));
        return (float) Math.max(0, Math.min(1, (GLOW_DISTANCE - distance) / GLOW_FADE));
    }

    /** {@code light} with its block light raised toward full as the glow does. */
    private static int lit(int light, float glow) {
        int block = Math.max(LightTexture.block(light), Math.round(15 * glow));
        return LightTexture.pack(block, LightTexture.sky(light));
    }

    /** A soft gold glow round where the item draws, pulsing gently, behind the item. */
    private static void drawHalo(PoseStack pose, VertexConsumer out, WallBuyPlate item, float glow) {
        // The client's own clock: the game time jumps when the server lags.
        float pulse = 0.8f + 0.2f * Mth.sin(Util.getMillis() / 1000f * 3);
        float strength = glow * pulse;
        int r = Math.round((HALO_COLOR >> 16 & 0xff) * strength);
        int g = Math.round((HALO_COLOR >> 8 & 0xff) * strength);
        int b = Math.round((HALO_COLOR & 0xff) * strength);
        float x0 = (float) (item.minX() - HALO_REACH);
        float y0 = (float) (item.minY() - HALO_REACH);
        float x1 = (float) (item.maxX() + HALO_REACH);
        float y1 = (float) (item.maxY() + HALO_REACH);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float[][] corners = {{x0, y0, 0, 1}, {x1, y0, 1, 1}, {x1, y1, 1, 0}, {x0, y1, 0, 0}};
        for (float[] c : corners) {
            out.vertex(matrix, c[0], c[1], HALO_Z).color(r, g, b, 255).uv(c[2], c[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(normal, 0, 0, -1).endVertex();
        }
    }

    /**
     * One layer of the plate: the frame ({@link WallBuyFrame}) laid out the plate's length along the wall, its thickness
     * standing out of the wall from {@link #BACK}, its width up the wall, each face showing its part of the layer's sprite.
     */
    private static void drawPlate(PoseStack pose, VertexConsumer out, WallBuyPlate plate, Skin skin, int light, int overlay) {
        WallBuyFrame frame = frame();
        if (frame == null) {
            return;
        }
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float length = (float) (plate.width() * 16);
        float centerX = (float) ((plate.minX() + plate.maxX()) / 2);
        float centerY = (float) ((plate.minY() + plate.maxY()) / 2);
        TextureAtlasSprite sprite = skin.sprite();
        for (WallBuyFrame.Box box : frame.laidOut(length)) {
            float[] a = box.from();
            float[] b = box.to();
            box.faces().forEach((side, uv) -> {
                // The face's corners in the model's pixels, in Minecraft's face layout: the first is its texture's top left.
                float[][] corners = switch (side) {
                    case UP -> new float[][] {{a[0], b[1], a[2]}, {b[0], b[1], a[2]}, {b[0], b[1], b[2]}, {a[0], b[1], b[2]}};
                    case DOWN -> new float[][] {{a[0], a[1], b[2]}, {b[0], a[1], b[2]}, {b[0], a[1], a[2]}, {a[0], a[1], a[2]}};
                    case NORTH -> new float[][] {{b[0], b[1], a[2]}, {a[0], b[1], a[2]}, {a[0], a[1], a[2]}, {b[0], a[1], a[2]}};
                    case SOUTH -> new float[][] {{a[0], b[1], b[2]}, {b[0], b[1], b[2]}, {b[0], a[1], b[2]}, {a[0], a[1], b[2]}};
                    case WEST -> new float[][] {{a[0], b[1], a[2]}, {a[0], b[1], b[2]}, {a[0], a[1], b[2]}, {a[0], a[1], a[2]}};
                    case EAST -> new float[][] {{b[0], b[1], b[2]}, {b[0], b[1], a[2]}, {b[0], a[1], a[2]}, {b[0], a[1], b[2]}};
                };
                float[][] uvs = {{uv.u0(), uv.v0()}, {uv.u1(), uv.v0()}, {uv.u1(), uv.v1()}, {uv.u0(), uv.v1()}};
                // The model's y comes out of the wall (toward the room, -z here), its z runs along the wall, its x up it.
                float[] facing = switch (side) {
                    case UP -> new float[] {0, 0, -1};
                    case DOWN -> new float[] {0, 0, 1};
                    case NORTH -> new float[] {-1, 0, 0};
                    case SOUTH -> new float[] {1, 0, 0};
                    case WEST -> new float[] {0, -1, 0};
                    case EAST -> new float[] {0, 1, 0};
                };
                for (int i = 0; i < 4; i++) {
                    float[] c = corners[i];
                    out.vertex(matrix, centerX + (c[2] - length / 2) / 16, centerY + (c[0] - 8) / 16, BACK - c[1] / 16)
                            .color(skin.color() >> 16 & 0xff, skin.color() >> 8 & 0xff, skin.color() & 0xff, 255)
                            .uv(sprite.getU(uvs[i][0]), sprite.getV(uvs[i][1]))
                            .overlayCoords(overlay)
                            .uv2(light)
                            .normal(normal, facing[0], facing[1], facing[2])
                            .endVertex();
                }
            });
        }
    }

    private static WallBuyFrame frame;
    private static boolean frameLoaded;

    /** The frame model, read once; nothing if it can't be read, which leaves wall-buys with no plate. */
    private static WallBuyFrame frame() {
        if (!frameLoaded) {
            frameLoaded = true;
            try (java.io.InputStream in = Minecraft.getInstance().getResourceManager().open(FRAME)) {
                frame = WallBuyFrame.load(in);
            } catch (java.io.IOException | RuntimeException e) {
                Hellstorm.LOG.error("Couldn't read the wall-buy frame {}", FRAME, e);
            }
        }
        return frame;
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
