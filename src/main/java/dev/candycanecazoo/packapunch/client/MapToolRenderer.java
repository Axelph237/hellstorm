package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.client.outline.ZoneOutlines;
import dev.candycanecazoo.packapunch.config.ClientConfig;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.SpawnLinks;
import dev.candycanecazoo.packapunch.map.WindowDef;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.tools.MapTool;
import dev.candycanecazoo.packapunch.tools.Selection;
import dev.candycanecazoo.packapunch.tools.ZoneWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** While a map tool is held, draws zones, the selection, spawn markers, the detection preview, and validation problems. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT)
public final class MapToolRenderer {
    private static final int SELECTION_OK = 0x68c586;
    private static final int SELECTION_BLOCKED = 0xc5b548;
    private static final int PREVIEW = 0x5bc0de;
    private static final int ERROR = 0xd9534f;
    private static final int WARNING = 0xf0ad4e;
    private static final int UNZONED = 0xffffff;

    /** Cluster outlines are only rebuilt when the slot changes, so the slot carries the zone's geometry. */
    private record ZoneSlot(ZoneId zone, List<BlockBox> boxes) {}

    private record ZoneBoxSlot(ZoneId zone, int box) {}

    private static final long MAX_CLUSTER_VOLUME = 65_536;

    private record PreviewSlot(int box) {}

    private record ProblemSlot(int index) {}

    private record SpawnSlot(BlockPos pos) {}

    private record WindowSlot(String id) {}

    private record LinkSlot(BlockPos marker) {}

    private static final Object SELECTION_SLOT = new Object();

    private MapToolRenderer() {}

    /** A stable, saturated color per zone id. */
    static int zoneColor(ZoneId id) {
        float hue = (id.value().hashCode() & 0xffff) / 65536f;
        return Mth.hsvToRgb(hue, 0.65f, 0.95f);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMapState.reset();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof MapTool)) {
            return;
        }
        double range = ClientConfig.OUTLINE_RANGE.get();
        double reach = ServerConfig.TOOL_REACH.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = eye.add(player.getViewVector(1.0f).scale(reach));
        AABB nearby = player.getBoundingBox().inflate(range);

        ZoneId hovered = hoveredZone(eye, look, nearby);
        for (Zone zone : ClientMapState.map().zones().values()) {
            if (zone.boxes().stream().noneMatch(b -> b.toAabb().intersects(nearby))) {
                continue;
            }
            int color = zoneColor(zone.id());
            float width = zone.id().equals(hovered) ? ZoneOutlines.THICK : ZoneOutlines.THIN;
            if (zone.volume() <= MAX_CLUSTER_VOLUME) {
                ZoneOutlines.cluster(new ZoneSlot(zone.id(), zone.boxes()), () -> cells(zone), color, width);
            } else {
                for (int i = 0; i < zone.boxes().size(); i++) {
                    ZoneOutlines.box(new ZoneBoxSlot(zone.id(), i), zone.boxes().get(i).toAabb(), color, width);
                }
            }
        }

        for (WindowDef window : ClientMapState.map().windows().values()) {
            AABB box = window.opening().toAabb();
            if (box.intersects(nearby)) {
                ZoneOutlines.box(new WindowSlot(window.id()), box.inflate(0.02), zoneColor(window.zone()), ZoneOutlines.THICK);
            }
        }

        Map<BlockPos, SpawnLinks.Link> links = ClientMapState.spawnLinks();
        for (BlockPos spawn : ClientMapState.map().features(FeatureKind.SPAWN)) {
            if (nearby.contains(Vec3.atCenterOf(spawn))) {
                SpawnLinks.Link link = links.get(spawn);
                int color = link == null ? UNZONED : zoneColor(link.zone());
                ZoneOutlines.box(new SpawnSlot(spawn), new AABB(spawn).deflate(0.3), color, ZoneOutlines.THICK);
                if (link != null) {
                    link.window().map(ClientMapState.map().windows()::get).ifPresent(w ->
                            ZoneOutlines.line(new LinkSlot(spawn), Vec3.atCenterOf(spawn), Vec3.atBottomCenterOf(w.attackSpot()).add(0, 0.5, 0),
                                    color, ZoneOutlines.THIN * 2));
                }
            }
        }

        drawSelection(player, reach);

        for (int i = 0; i < ClientMapState.preview().size(); i++) {
            ZoneOutlines.box(new PreviewSlot(i), ClientMapState.preview().get(i).toAabb(), PREVIEW, ZoneOutlines.THIN * 2);
        }

        for (int i = 0; i < ClientMapState.problems().size(); i++) {
            Problem problem = ClientMapState.problems().get(i);
            int color = problem.severity() == Problem.Severity.ERROR ? ERROR : WARNING;
            int index = i;
            problem.pos().ifPresent(pos -> ZoneOutlines.box(new ProblemSlot(index), new AABB(pos).inflate(0.05), color, ZoneOutlines.THICK));
        }
    }

    private static List<BlockPos> cells(Zone zone) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockBox box : zone.boxes()) {
            BlockPos.betweenClosed(box.min(), box.max()).forEach(p -> cells.add(p.immutable()));
        }
        return cells;
    }

    private static ZoneId hoveredZone(Vec3 eye, Vec3 look, AABB nearby) {
        ZoneId best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Zone zone : ClientMapState.map().zones().values()) {
            for (int i = 0; i < zone.boxes().size(); i++) {
                AABB box = zone.boxes().get(i).toAabb();
                if (!box.intersects(nearby)) {
                    continue;
                }
                Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, look);
                if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestDistance) {
                    bestDistance = hit.get().distanceToSqr(eye);
                    best = zone.id();
                }
            }
        }
        return best;
    }

    private static void drawSelection(LocalPlayer player, double reach) {
        Selection selection = ClientMapState.selection();
        if (selection.first().isEmpty()) {
            return;
        }
        Optional<BlockPos> other = selection.second().or(() -> ZoneWandItem.targetCell(player, reach));
        boolean reachable = other.isPresent();
        BlockBox box = BlockBox.of(selection.first().get(), other.orElse(selection.first().get()));
        boolean overlaps = ClientMapState.map().zones().values().stream()
                .anyMatch(z -> z.boxes().stream().anyMatch(b -> b.intersects(box)));
        int color = reachable && !overlaps ? SELECTION_OK : SELECTION_BLOCKED;
        ZoneOutlines.box(SELECTION_SLOT, box.toAabb(), color, ZoneOutlines.THICK);
    }
}
