package dev.candycanecazoo.hellstorm.gen.place;

import dev.candycanecazoo.hellstorm.gen.block.ConnectorBlock;
import dev.candycanecazoo.hellstorm.gen.block.PlaceholderBlock;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.gen.layout.Size;
import dev.candycanecazoo.hellstorm.gen.pack.RoomDef;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Saves the room a {@link RoomBlock} marks into its pack: the box's blocks as a structure template; its connectors, read
 * from the connector blocks (touching ones facing the same way with the same settings are one opening); its
 * placeholders; and its slice of the level's map (zones clipped to the box, and the windows, doors and features inside
 * it), all relative to the box's corner. A room that couldn't join anything, or whose connectors aren't on its sides, is
 * refused.
 */
public final class RoomSaver {
    /** A save's outcome: what was saved, with warnings, or why nothing was. */
    public record Result(boolean saved, String message, List<String> warnings) {}

    private RoomSaver() {}

    /** Where packs live: {@code hellstorm/maps/} in the game folder (the server folder on a server). */
    public static Path packs() {
        return FMLPaths.GAMEDIR.get().resolve("hellstorm").resolve("maps");
    }

    public static Result save(ServerLevel level, RoomBlock.Entity block) {
        Result result = save(level, block.config(), block.min(), packs());
        dev.candycanecazoo.hellstorm.Hellstorm.LOG.info("Room Block at {}: {}{}", block.getBlockPos().toShortString(), result.message(),
                result.warnings().isEmpty() ? "" : " (" + String.join("; ", result.warnings()) + ")");
        return result;
    }

    public static Result save(ServerLevel level, RoomBlock.Config config, BlockPos min, Path packs) {
        Optional<String> problem = config.problem();
        if (problem.isPresent()) {
            return new Result(false, problem.get(), List.of());
        }
        Vec3i size = config.size();
        BlockPos max = min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        RoomDef def = read(level, config, min, max);
        List<String> problems = def.piece(def.id()).problems();
        if (!problems.isEmpty()) {
            return new Result(false, String.join("; ", problems), List.of());
        }
        List<String> warnings = new ArrayList<>();
        if (def.slice().zones().isEmpty()) {
            warnings.add("The room has no zone, so players in it are in no room and zombies there have nowhere to go");
        } else if (def.slice().features(FeatureKind.SPAWN).isEmpty()) {
            warnings.add("The room has no spawn marker, so no zombies come from it");
        }
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, null);
        try {
            RoomPack.writeRoom(packs, config.pack(), def, template.save(new CompoundTag()));
        } catch (IOException e) {
            return new Result(false, "Couldn't write the room: " + e.getMessage(), List.of());
        }
        return new Result(true, "Saved room " + def.id() + " (" + size.getX() + " x " + size.getY() + " x " + size.getZ() + ", "
                + def.connectors().size() + " connectors, " + def.placeholders().size() + " placeholders) to pack " + config.pack(), warnings);
    }

    /** The room's definition, read from the blocks and the map between {@code min} and {@code max}. */
    static RoomDef read(ServerLevel level, RoomBlock.Config config, BlockPos min, BlockPos max) {
        List<RoomDef.Placeholder> placeholders = new ArrayList<>();
        Map<BlockPos, ConnectorKey> connectorBlocks = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PlaceholderBlock placeholder) {
                BlockPos rel = pos.subtract(min);
                placeholders.add(new RoomDef.Placeholder(placeholder.kind(), rel.getX(), rel.getY(), rel.getZ(),
                        RoomTransform.facing(state.getValue(HorizontalDirectionalBlock.FACING))));
            } else if (state.getBlock() instanceof ConnectorBlock && level.getBlockEntity(pos) instanceof ConnectorBlock.Entity entity) {
                connectorBlocks.put(pos.immutable(), new ConnectorKey(state.getValue(HorizontalDirectionalBlock.FACING), entity.config()));
            }
        }
        List<Connector> connectors = connectors(connectorBlocks, min);
        Vec3i size = config.size();
        return new RoomDef(config.room(), new Size(size.getX(), size.getY(), size.getZ()), Set.copyOf(config.tags()), config.weight(),
                config.max(), connectors, placeholders, slice(MapSavedData.get(level).map(), BlockBox.of(min, max), min));
    }

    private record ConnectorKey(Direction facing, ConnectorBlock.Config config) {}

    /** Touching connector blocks that face the same way with the same settings, as one opening each, ids by position. */
    private static List<Connector> connectors(Map<BlockPos, ConnectorKey> blocks, BlockPos min) {
        List<Connector> out = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        Comparator<BlockPos> order = Comparator.comparingInt(BlockPos::getY);
        List<BlockPos> ordered = blocks.keySet().stream().sorted(order.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
        for (BlockPos start : ordered) {
            if (!done.add(start)) {
                continue;
            }
            ConnectorKey key = blocks.get(start);
            List<BlockPos> group = new ArrayList<>(List.of(start));
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(group);
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                for (Direction d : Direction.values()) {
                    BlockPos next = at.relative(d);
                    if (key.equals(blocks.get(next)) && done.add(next)) {
                        group.add(next);
                        queue.add(next);
                    }
                }
            }
            BlockBox bounds = group.stream().map(BlockBox::single).reduce(BlockBox::union).orElseThrow();
            Box opening = new Box(bounds.minX() - min.getX(), bounds.minY() - min.getY(), bounds.minZ() - min.getZ(),
                    bounds.maxX() - min.getX(), bounds.maxY() - min.getY(), bounds.maxZ() - min.getZ());
            out.add(new Connector("c" + out.size(), opening, RoomTransform.facing(key.facing()), key.config().kind(), key.config().group()));
        }
        return out;
    }

    /** The part of {@code map} inside {@code box}, moved so {@code min} is 0, 0, 0. */
    static ZombiesMap slice(ZombiesMap map, BlockBox box, BlockPos min) {
        Map<ZoneId, Zone> zones = new TreeMap<>();
        map.zones().forEach((id, zone) -> {
            List<BlockBox> inside = zone.boxes().stream().map(b -> clip(b, box)).flatMap(Optional::stream)
                    .map(b -> b.moved(-min.getX(), -min.getY(), -min.getZ())).toList();
            if (!inside.isEmpty()) {
                zones.put(id, new Zone(id, inside, false));
            }
        });
        Map<BlockPos, FeatureKind> features = new LinkedHashMap<>();
        map.features().forEach((pos, kind) -> {
            if (box.contains(pos)) {
                features.put(pos.subtract(min), kind);
            }
        });
        Map<String, WindowDef> windows = new TreeMap<>();
        map.windows().forEach((id, w) -> {
            if (clip(w.opening(), box).filter(w.opening()::equals).isPresent()) {
                windows.put(id, new WindowDef(id, w.opening().moved(-min.getX(), -min.getY(), -min.getZ()), w.outward(), w.maxBoards()));
            }
        });
        Map<String, DoorDef> doors = new TreeMap<>();
        map.doors().forEach((id, d) -> {
            if (!d.members().isEmpty() && d.members().stream().allMatch(box::contains)) {
                doors.put(id, new DoorDef(id, d.members().stream().map(p -> p.subtract(min)).collect(java.util.stream.Collectors.toSet()),
                        d.cost(), d.requiresPower(), d.style()));
            }
        });
        return new ZombiesMap(zones, features, windows, doors);
    }

    private static Optional<BlockBox> clip(BlockBox a, BlockBox b) {
        int x0 = Math.max(a.minX(), b.minX());
        int y0 = Math.max(a.minY(), b.minY());
        int z0 = Math.max(a.minZ(), b.minZ());
        int x1 = Math.min(a.maxX(), b.maxX());
        int y1 = Math.min(a.maxY(), b.maxY());
        int z1 = Math.min(a.maxZ(), b.maxZ());
        return x0 <= x1 && y0 <= y1 && z0 <= z1 ? Optional.of(new BlockBox(x0, y0, z0, x1, y1, z1)) : Optional.empty();
    }
}
