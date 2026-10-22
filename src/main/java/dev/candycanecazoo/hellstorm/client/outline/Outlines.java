package dev.candycanecazoo.hellstorm.client.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.WhiteTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outlines drawn as thin solid boxes, ported from the Outliner in Catnip (Ponder 1.0.92, MIT; see THIRD_PARTY_NOTICES) so
 * they look and behave as they did: each slot lives one client tick after it was last shown, then fades over
 * {@link #FADE_TICKS} by thinning its lines, and is gone. Client thread only.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class Outlines {
    static final int FADE_TICKS = 8;

    sealed interface Shape permits Box, Line, Cluster {}

    record Box(AABB box) implements Shape {}

    record Line(Vec3 from, Vec3 to) implements Shape {}

    /** The edges where a set of blocks' outer surface folds, relative to {@code anchor}. */
    record Cluster(BlockPos anchor, List<Edge> edges) implements Shape {}

    /** A block edge one block long, from {@code pos} along the positive {@code axis}. */
    record Edge(Direction.Axis axis, BlockPos pos) {}

    private static final class Entry {
        Shape shape;
        int color;
        float lineWidth;
        int ticksTillRemoval = 1;

        Entry(Shape shape) {
            this.shape = shape;
        }

        float alpha(float partialTick) {
            return fade(ticksTillRemoval, partialTick);
        }
    }

    /** Alpha cubed, eased between this tick and the last, as Catnip fades: 1 while shown, toward 0 over the fade. */
    static float fade(int ticksTillRemoval, float partialTick) {
        if (ticksTillRemoval >= 0) {
            return 1;
        }
        int previous = ticksTillRemoval + 1;
        float last = previous >= 0 ? 1 : 1 + previous / (float) FADE_TICKS;
        float current = 1 + ticksTillRemoval / (float) FADE_TICKS;
        float alpha = Mth.lerp(partialTick, last, current);
        return alpha * alpha * alpha;
    }

    private static final Map<Object, Entry> ENTRIES = new HashMap<>();

    private Outlines() {}

    public static void box(Object slot, AABB box, int color, float lineWidth) {
        show(slot, new Box(box), color, lineWidth);
    }

    public static void line(Object slot, Vec3 from, Vec3 to, int color, float lineWidth) {
        show(slot, new Line(from, to), color, lineWidth);
    }

    /** Makes a new cluster outline in the slot, replacing whatever was there. */
    public static void cluster(Object slot, Iterable<BlockPos> blocks, int color, float lineWidth) {
        ENTRIES.remove(slot);
        show(slot, cluster(blocks), color, lineWidth);
    }

    /** Keeps the slot alive another tick with new colors, if it exists. */
    public static boolean keep(Object slot, int color, float lineWidth) {
        Entry entry = ENTRIES.get(slot);
        if (entry == null) {
            return false;
        }
        entry.ticksTillRemoval = 1;
        entry.color = color;
        entry.lineWidth = lineWidth;
        return true;
    }

    private static void show(Object slot, Shape shape, int color, float lineWidth) {
        Entry entry = ENTRIES.computeIfAbsent(slot, s -> new Entry(shape));
        entry.shape = shape;
        entry.ticksTillRemoval = 1;
        entry.color = color;
        entry.lineWidth = lineWidth;
    }

    /**
     * Every block edge where the surface folds: each block toggles its twelve edges, so an edge two or four blocks share
     * cancels out, and one on the outside of a corner or the inside of a bend stays.
     */
    static Cluster cluster(Iterable<BlockPos> blocks) {
        BlockPos anchor = null;
        Set<Edge> edges = new HashSet<>();
        for (BlockPos block : blocks) {
            if (anchor == null) {
                anchor = block.immutable();
            }
            BlockPos pos = block.subtract(anchor);
            for (Direction.Axis axis : Direction.Axis.values()) {
                Direction.Axis[] across = across(axis);
                Direction first = Direction.get(Direction.AxisDirection.POSITIVE, across[0]);
                Direction second = Direction.get(Direction.AxisDirection.POSITIVE, across[1]);
                for (int a = 0; a <= 1; a++) {
                    for (int b = 0; b <= 1; b++) {
                        Edge edge = new Edge(axis, pos.relative(first, a).relative(second, b));
                        if (!edges.remove(edge)) {
                            edges.add(edge);
                        }
                    }
                }
            }
        }
        return new Cluster(anchor == null ? BlockPos.ZERO : anchor, List.copyOf(edges));
    }

    private static Direction.Axis[] across(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null) {
            return;
        }
        Iterator<Entry> entries = ENTRIES.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            entry.ticksTillRemoval--;
            if (entry.ticksTillRemoval < -FADE_TICKS) {
                entries.remove();
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ENTRIES.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entitySolid(WhiteTexture.get());
        VertexConsumer out = buffers.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        for (Entry entry : ENTRIES.values()) {
            float alpha = entry.alpha(event.getPartialTick());
            if (alpha < 1 / 8f) {
                continue;
            }
            float width = alpha * entry.lineWidth;
            if (width == 0) {
                continue;
            }
            Vector4f color = new Vector4f((entry.color >> 16 & 0xff) / 255f, (entry.color >> 8 & 0xff) / 255f, (entry.color & 0xff) / 255f, alpha);
            if (entry.shape instanceof Box box) {
                box(pose, out, camera, box.box(), width, color);
            } else if (entry.shape instanceof Line line) {
                line(pose, out, camera, line.from(), line.to(), width, color);
            } else if (entry.shape instanceof Cluster cluster) {
                cluster(pose, out, camera, cluster, width, color);
            }
        }
        buffers.endBatch(type);
    }

    /** From inside the box its edges are pulled in a little, from outside pushed out, so the walls they lie on don't hide them. */
    private static void box(PoseStack pose, VertexConsumer out, Vec3 camera, AABB box, float width, Vector4f color) {
        float inflate = box.contains(camera) ? -1 / 128f : 1 / 128f;
        AABB local = box.move(camera.scale(-1));
        float x0 = (float) local.minX - inflate;
        float y0 = (float) local.minY - inflate;
        float z0 = (float) local.minZ - inflate;
        float x1 = (float) local.maxX + inflate;
        float y1 = (float) local.maxY + inflate;
        float z1 = (float) local.maxZ + inflate;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        PoseStack.Pose last = pose.last();
        edge(last, out, x0, y0, z0, Direction.EAST, dx, width, color);
        edge(last, out, x0, y0, z0, Direction.UP, dy, width, color);
        edge(last, out, x0, y0, z0, Direction.SOUTH, dz, width, color);
        edge(last, out, x1, y0, z0, Direction.UP, dy, width, color);
        edge(last, out, x1, y0, z0, Direction.SOUTH, dz, width, color);
        edge(last, out, x0, y1, z0, Direction.EAST, dx, width, color);
        edge(last, out, x0, y1, z0, Direction.SOUTH, dz, width, color);
        edge(last, out, x0, y0, z1, Direction.EAST, dx, width, color);
        edge(last, out, x0, y0, z1, Direction.UP, dy, width, color);
        edge(last, out, x0, y1, z1, Direction.EAST, dx, width, color);
        edge(last, out, x1, y0, z1, Direction.UP, dy, width, color);
        edge(last, out, x1, y1, z0, Direction.SOUTH, dz, width, color);
    }

    /** A line from {@code from} to {@code to}: one edge along +z, turned to point the right way. */
    private static void line(PoseStack pose, VertexConsumer out, Vec3 camera, Vec3 from, Vec3 to, float width, Vector4f color) {
        pose.pushPose();
        pose.translate(from.x - camera.x, from.y - camera.y, from.z - camera.z);
        pose.mulPose(aim(to.subtract(from)));
        edge(pose.last(), out, 0, 0, 0, Direction.SOUTH, (float) to.distanceTo(from), width, color);
        pose.popPose();
    }

    /** The turn that takes +z onto {@code direction}: about y to its heading, then about x to its pitch. */
    static Quaternionf aim(Vec3 direction) {
        float heading = (float) Math.toDegrees(Mth.atan2(direction.x, direction.z));
        float flat = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        float pitch = (float) Math.toDegrees(Mth.atan2(flat, direction.y)) - 90;
        return Axis.YP.rotationDegrees(heading).mul(Axis.XP.rotationDegrees(pitch));
    }

    private static void cluster(PoseStack pose, VertexConsumer out, Vec3 camera, Cluster cluster, float width, Vector4f color) {
        pose.pushPose();
        pose.translate(cluster.anchor().getX() - camera.x, cluster.anchor().getY() - camera.y, cluster.anchor().getZ() - camera.z);
        PoseStack.Pose last = pose.last();
        for (Edge edge : cluster.edges()) {
            edge(last, out, edge.pos().getX(), edge.pos().getY(), edge.pos().getZ(),
                    Direction.get(Direction.AxisDirection.POSITIVE, edge.axis()), 1, width, color);
        }
        pose.popPose();
    }

    /** A square rod {@code width} thick, centered on the origin's corner, running {@code length} along {@code along}. */
    private static void edge(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, Direction along, float length,
                             float width, Vector4f color) {
        float half = width / 2;
        Vector3f min = new Vector3f(x - half, y - half, z - half);
        Vector3f max = new Vector3f(x + half, y + half, z + half);
        max.add(along.getStepX() * length, along.getStepY() * length, along.getStepZ() * length);
        cuboid(pose, out, min, max, color);
    }

    /** Catnip's cuboid: the eight corners, then each face's four in its winding, with that face's normal. */
    private static void cuboid(PoseStack.Pose pose, VertexConsumer out, Vector3f min, Vector3f max, Vector4f color) {
        Matrix4f matrix = pose.pose();
        Vector3f[] c = {
                corner(matrix, min.x, min.y, max.z), corner(matrix, min.x, min.y, min.z),
                corner(matrix, max.x, min.y, min.z), corner(matrix, max.x, min.y, max.z),
                corner(matrix, min.x, max.y, min.z), corner(matrix, min.x, max.y, max.z),
                corner(matrix, max.x, max.y, max.z), corner(matrix, max.x, max.y, min.z)};
        Matrix3f normals = pose.normal();
        face(out, normals, color, 0, -1, 0, c[0], c[1], c[2], c[3]);
        face(out, normals, color, 0, 1, 0, c[4], c[5], c[6], c[7]);
        face(out, normals, color, 0, 0, -1, c[7], c[2], c[1], c[4]);
        face(out, normals, color, 0, 0, 1, c[5], c[0], c[3], c[6]);
        face(out, normals, color, -1, 0, 0, c[4], c[1], c[0], c[5]);
        face(out, normals, color, 1, 0, 0, c[6], c[3], c[2], c[7]);
    }

    private static Vector3f corner(Matrix4f matrix, float x, float y, float z) {
        Vector4f at = new Vector4f(x, y, z, 1).mul(matrix);
        return new Vector3f(at.x(), at.y(), at.z());
    }

    private static final float[][] UVS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private static void face(VertexConsumer out, Matrix3f normals, Vector4f color, float nx, float ny, float nz, Vector3f... corners) {
        Vector3f normal = new Vector3f(nx, ny, nz).mul(normals);
        for (int i = 0; i < 4; i++) {
            out.vertex(corners[i].x(), corners[i].y(), corners[i].z())
                    .color(color.x(), color.y(), color.z(), color.w())
                    .uv(UVS[i][0], UVS[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x(), normal.y(), normal.z())
                    .endVertex();
        }
    }
}
