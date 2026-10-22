package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.outline.ZoneOutlines;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.SpawnLinks;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.tools.MapTool;
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.ZoneWandItem;
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
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class MapToolRenderer {
    private static final int SELECTION_OK = 0x68c586;
    private static final int SELECTION_BLOCKED = 0xc5b548;
    private static final int PREVIEW = 0x5bc0de;
    private static final int ERROR = 0xd9534f;
    private static final int WARNING = 0xf0ad4e;
    private static final int UNZONED = 0xffffff;
    private static final int TRAP_READY = 0x55ff55;
    private static final int TRAP_ACTIVE = 0xff5555;
    private static final int TRAP_COOLDOWN = 0xffaa00;

    /** Cluster outlines are only rebuilt when the slot changes, so the slot carries the zone's geometry. */
    private record ZoneSlot(ZoneId zone, List<BlockBox> boxes) {}

    private record ZoneBoxSlot(ZoneId zone, int box) {}

    private static final long MAX_CLUSTER_VOLUME = 65_536;

    private record PreviewSlot(int box) {}

    private record SelectionSlot(int box) {}

    private record ProblemSlot(int index) {}

    private record SpawnSlot(BlockPos pos) {}

    private record TrapSlot(BlockPos pos) {}

    private record WindowSlot(String id) {}

    private record LinkSlot(BlockPos marker) {}

    private record PathSlot(int entity, int segment) {}

    private record DoorwaySlot(BlockPos cell) {}

    private record DoorSlot(String id, java.util.Set<BlockPos> members) {}

    private static final int PATH = 0xff5555;
    private static final int DOORWAY = 0xb06cff;
    private static final int DOOR = 0xd9a441;

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

        for (dev.candycanecazoo.hellstorm.map.DoorDef door : ClientMapState.map().doors().values()) {
            if (door.members().isEmpty() || !new AABB(door.members().iterator().next()).intersects(nearby)) {
                continue;
            }
            ZoneOutlines.cluster(new DoorSlot(door.id(), door.members()), door::members, DOOR, ZoneOutlines.THIN);
        }

        for (WindowDef window : ClientMapState.map().windows().values()) {
            AABB box = window.opening().toAabb();
            if (box.intersects(nearby)) {
                ZoneOutlines.box(new WindowSlot(window.id()), box.inflate(0.02), zoneColor(window.zone()), ZoneOutlines.THICK);
            }
        }

        for (BlockPos trap : ClientMapState.map().features(FeatureKind.TRAP)) {
            if (nearby.contains(Vec3.atCenterOf(trap))) {
                ZoneOutlines.box(new TrapSlot(trap), new AABB(trap).inflate(0.05), trapColor(player.level().getBlockEntity(trap)), ZoneOutlines.THICK);
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

        ClientMapState.debugPaths().ifPresent(paths -> {
            for (var path : paths.paths()) {
                for (int i = 0; i + 1 < path.nodes().size(); i++) {
                    ZoneOutlines.line(new PathSlot(path.entityId(), i), Vec3.atBottomCenterOf(path.nodes().get(i)).add(0, 0.1, 0),
                            Vec3.atBottomCenterOf(path.nodes().get(i + 1)).add(0, 0.1, 0), PATH, ZoneOutlines.THIN * 3);
                }
            }
            for (BlockPos cell : paths.doorways()) {
                ZoneOutlines.box(new DoorwaySlot(cell), new AABB(cell).deflate(0.2), DOORWAY, ZoneOutlines.THICK);
            }
        });

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

    /** Each block once: the cluster outline toggles a face per block that has it, so a block listed twice cancels out. */
    private static java.util.Set<BlockPos> cells(Zone zone) {
        java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>();
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

    /** Each box of the selection, and the next one from its first corner to the cursor, yellow where it overlaps a room. */
    private static void drawSelection(LocalPlayer player, double reach) {
        Selection selection = ClientMapState.selection();
        for (int i = 0; i < selection.boxes().size(); i++) {
            BlockBox box = selection.boxes().get(i);
            ZoneOutlines.box(new SelectionSlot(i), box.toAabb(), overlapsRoom(box) ? SELECTION_BLOCKED : SELECTION_OK, ZoneOutlines.THICK);
        }
        if (selection.corner().isEmpty()) {
            return;
        }
        Optional<BlockPos> other = ZoneWandItem.targetCell(player, reach);
        BlockBox box = BlockBox.of(selection.corner().get(), other.orElse(selection.corner().get()));
        int color = other.isPresent() && !overlapsRoom(box) ? SELECTION_OK : SELECTION_BLOCKED;
        ZoneOutlines.box(SELECTION_SLOT, box.toAabb(), color, ZoneOutlines.THICK);
    }

    private static boolean overlapsRoom(BlockBox box) {
        return ClientMapState.map().zones().values().stream().anyMatch(z -> z.boxes().stream().anyMatch(b -> b.intersects(box)));
    }

    private static int trapColor(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if (!(entity instanceof dev.candycanecazoo.hellstorm.feature.TrapControllerBlock.Entity trap)) {
            return UNZONED;
        }
        return switch (trap.cycle().phase()) {
            case READY -> TRAP_READY;
            case ACTIVE -> TRAP_ACTIVE;
            case COOLDOWN -> TRAP_COOLDOWN;
        };
    }
}
