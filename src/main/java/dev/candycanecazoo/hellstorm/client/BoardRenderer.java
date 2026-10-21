package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.feature.BoardLayout;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws each window's boards as long planks across the whole opening (see {@link BoardLayout}), nailed onto the wall
 * just in front of the opening's inside face so their ends show on the wall either side. Uses the vanilla oak planks texture: one plank row of it, stretched end to end along each board. The barricade
 * blocks themselves render nothing; their {@code boards} blockstate says how many boards to draw.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class BoardRenderer {
    private static final ResourceLocation PLANKS = new ResourceLocation("minecraft", "block/oak_planks");
    private static final double RANGE_SQR = 128 * 128;
    /** How thick a board is. Two layers of boards stand out from the wall by about as much as the panel players aim at. */
    private static final double THICKNESS = 1.3 / 16;
    private static final double LAYER_GAP = 1.4 / 16;
    /** Keeps the back of the boards off the wall face so they never flicker against it. */
    private static final double FACE_GAP = 0.2 / 16;

    private BoardRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ClientMapState.map().windows().isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLANKS);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer out = buffers.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (WindowDef window : ClientMapState.map().windows().values()) {
            BlockBox opening = window.opening();
            if (opening.min().getCenter().distanceToSqr(camera) > RANGE_SQR || !event.getFrustum().isVisible(opening.toAabb().inflate(0.5))) {
                continue;
            }
            BlockState state = level.getBlockState(opening.min());
            int boards = state.is(PapRegistries.BARRICADE.get()) ? state.getValue(BarricadeBlock.BOARDS) : 0;
            if (boards == 0) {
                continue;
            }
            Frame frame = Frame.of(window);
            int light = LevelRenderer.getLightColor(level, opening.min().relative(window.outward().getOpposite()));
            List<BoardLayout.Board> layout = BoardLayout.of(window.id(), window.width(), window.height(), window.maxBoards());
            for (int i = 0; i < Math.min(boards, layout.size()); i++) {
                board(out, pose, frame, layout.get(i), sprite, light);
            }
        }
        pose.popPose();
        buffers.endBatch(type);
    }

    /**
     * World coordinates for a window's plane: {@code origin} is the bottom end of the opening's inside face where
     * {@code u = 0}, {@code along} runs across the window, and {@code out} points from the inside face toward the outside
     * (boards sit at negative depths, on the room side).
     */
    private record Frame(Vec3 origin, Vec3 along, Vec3 out) {
        static Frame of(WindowDef window) {
            BlockBox box = window.opening();
            Direction inward = window.outward().getOpposite();
            Direction along = window.outward().getClockWise();
            double face = coordinate(inward, box);
            double start = along.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? min(along.getAxis(), box) : max(along.getAxis(), box) + 1;
            double x = inward.getAxis() == Direction.Axis.X ? face : start;
            double z = inward.getAxis() == Direction.Axis.Z ? face : start;
            return new Frame(new Vec3(x, box.minY(), z), Vec3.atLowerCornerOf(along.getNormal()), Vec3.atLowerCornerOf(window.outward().getNormal()));
        }

        /** The opening's face on {@code side}. */
        private static double coordinate(Direction side, BlockBox box) {
            return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max(side.getAxis(), box) + 1 : min(side.getAxis(), box);
        }

        private static int min(Direction.Axis axis, BlockBox box) {
            return axis == Direction.Axis.X ? box.minX() : box.minZ();
        }

        private static int max(Direction.Axis axis, BlockBox box) {
            return axis == Direction.Axis.X ? box.maxX() : box.maxZ();
        }

        Vec3 at(double u, double v, double depth) {
            return origin.add(along.scale(u)).add(0, v, 0).add(out.scale(depth));
        }
    }

    private static void board(VertexConsumer out, PoseStack pose, Frame frame, BoardLayout.Board b, TextureAtlasSprite sprite, int light) {
        double cos = Math.cos(b.angle());
        double sin = Math.sin(b.angle());
        double halfLength = b.length() / 2;
        double halfWidth = b.width() / 2;
        double near = -(FACE_GAP + b.layer() * LAYER_GAP);
        double far = near - THICKNESS;
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double s1 = (i & 1) == 0 ? -halfLength : halfLength;
            double s2 = (i & 2) == 0 ? -halfWidth : halfWidth;
            double depth = (i & 4) == 0 ? near : far;
            c[i] = frame.at(b.u() + s1 * cos - s2 * sin, b.v() + s1 * sin + s2 * cos, depth);
        }
        Vec3 lengthwise = frame.along().scale(cos).add(0, sin, 0);
        Vec3 across = frame.along().scale(-sin).add(0, cos, 0);
        Vec3 outward = frame.out();
        // One plank row of the texture (4 of its 16 pixels tall) runs the full length of the board.
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV(b.plankRow() * 4);
        float v1 = sprite.getV(b.plankRow() * 4 + 4);
        float edge = sprite.getV(b.plankRow() * 4 + 1);
        float endU = sprite.getU(4);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(out, m, n, light, outward, c[0], c[1], c[3], c[2], u0, v1, u1, v0);
        quad(out, m, n, light, outward.reverse(), c[4], c[5], c[7], c[6], u0, v1, u1, v0);
        quad(out, m, n, light, across, c[2], c[3], c[7], c[6], u0, v0, u1, edge);
        quad(out, m, n, light, across.reverse(), c[0], c[1], c[5], c[4], u0, v0, u1, edge);
        quad(out, m, n, light, lengthwise, c[1], c[3], c[7], c[5], u0, v0, endU, edge);
        quad(out, m, n, light, lengthwise.reverse(), c[0], c[2], c[6], c[4], u0, v0, endU, edge);
    }

    /** {@code a b c d} go round the quad; {@code a} gets {@code (ua, va)}, {@code c} gets {@code (uc, vc)}. */
    private static void quad(VertexConsumer out, Matrix4f m, Matrix3f n, int light, Vec3 normal, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float ua, float va, float uc, float vc) {
        vertex(out, m, n, light, normal, a, ua, va);
        vertex(out, m, n, light, normal, b, uc, va);
        vertex(out, m, n, light, normal, c, uc, vc);
        vertex(out, m, n, light, normal, d, ua, vc);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f n, int light, Vec3 normal, Vec3 p, float u, float v) {
        out.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}
