package dev.candycanecazoo.hellstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.tools.MapTool;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * While a map tool is held, each room's outer surface is filled in its outline color at {@link #ALPHA}, from inside and
 * out, over the walls and floors it lies on.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class ZoneFaces {
    private static final float ALPHA = 0.25f;
    /** Bigger zones draw each box's sides whole, inner sides and all, instead of finding the surface block by block. */
    private static final long MAX_SURFACE_VOLUME = 65_536;

    /** One side of a box. */
    private record Face(BlockBox box, Direction side) {}

    private static final Map<Zone, List<Face>> SURFACES = new HashMap<>();

    private ZoneFaces() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof MapTool)) {
            SURFACES.clear();
            return;
        }
        AABB nearby = player.getBoundingBox().inflate(ClientConfig.OUTLINE_RANGE.get());
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = SeeThrough.decal(WhiteTexture.get());
        VertexConsumer out = buffers.getBuffer(type);
        Set<Zone> shown = new HashSet<>();
        for (Zone zone : ClientMapState.map().zones().values()) {
            if (zone.boxes().stream().noneMatch(b -> b.toAabb().intersects(nearby))) {
                continue;
            }
            shown.add(zone);
            int color = MapToolRenderer.zoneColor(zone.id());
            float r = (color >> 16 & 0xff) / 255f;
            float g = (color >> 8 & 0xff) / 255f;
            float b = (color & 0xff) / 255f;
            for (Face face : SURFACES.computeIfAbsent(zone, ZoneFaces::surface)) {
                draw(out, pose, camera, face, r, g, b);
            }
        }
        SURFACES.keySet().retainAll(shown);
        buffers.endBatch(type);
    }

    /** Every block side of the zone with no block of the zone beyond it. */
    private static List<Face> surface(Zone zone) {
        List<Face> faces = new ArrayList<>();
        if (zone.volume() > MAX_SURFACE_VOLUME) {
            for (BlockBox box : zone.boxes()) {
                for (Direction side : Direction.values()) {
                    faces.add(new Face(box, side));
                }
            }
            return faces;
        }
        LongSet cells = new LongOpenHashSet();
        for (BlockBox box : zone.boxes()) {
            BlockPos.betweenClosed(box.min(), box.max()).forEach(p -> cells.add(p.asLong()));
        }
        cells.forEach((long cell) -> {
            BlockPos pos = BlockPos.of(cell);
            for (Direction side : Direction.values()) {
                if (!cells.contains(BlockPos.offset(cell, side))) {
                    faces.add(new Face(BlockBox.single(pos), side));
                }
            }
        });
        return faces;
    }

    private static void draw(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Face face, float r, float g, float b) {
        int a = face.side().getAxis().ordinal();
        int u = (a + 1) % 3;
        int v = (a + 2) % 3;
        BlockBox box = face.box();
        int[] min = {box.minX(), box.minY(), box.minZ()};
        int[] max = {box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1};
        int at = face.side().getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[a] : min[a];
        int[][] corners = {{min[u], min[v]}, {max[u], min[v]}, {max[u], max[v]}, {min[u], max[v]}};
        double[] cam = {camera.x, camera.y, camera.z};
        double[] p = new double[3];
        for (int[] corner : corners) {
            p[a] = at;
            p[u] = corner[0];
            p[v] = corner[1];
            out.vertex(pose.pose(), (float) (p[0] - cam[0]), (float) (p[1] - cam[1]), (float) (p[2] - cam[2]))
                    .color(r, g, b, ALPHA)
                    .uv(corner[0] - min[u], corner[1] - min[v])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(), face.side().getStepX(), face.side().getStepY(), face.side().getStepZ())
                    .endVertex();
        }
    }
}
