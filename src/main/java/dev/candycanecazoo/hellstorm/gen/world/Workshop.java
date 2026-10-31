package dev.candycanecazoo.hellstorm.gen.world;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.gen.block.ConnectorBlock;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.Depths;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.FeatureRule;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.PoolEntry;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.Range;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.TagSpec;
import dev.candycanecazoo.hellstorm.gen.pack.PlaceholderKind;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.registry.GenRegistries;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The map workshop's boilerplate: a row of brick rooms and a hallway on a stone platform, each with its connectors,
 * placeholders, zone and spawn marker already in, and a Room Block before it set to save it into the {@value #PACK}
 * pack. Saving all of them makes a pack that generates a playable map, with the starter {@code generation.json} this
 * writes. A starting point to change, copy and add to.
 */
public final class Workshop {
    public static final String PACK = "workshop";
    private static final int HEIGHT = 6;
    private static final int GAP = 6;

    /** A boilerplate room: its name and size, tags, weight and max, which sides have an opening and what kind, and what's inside. */
    record Sample(String id, int sx, int sz, List<String> tags, int weight, Optional<Integer> max, Map<Direction, Connector.Kind> openings,
                  List<Thing> things) {}

    /** A placeholder or spawn marker inside a room, relative to its corner, facing {@code facing}. */
    record Thing(Optional<PlaceholderKind> kind, int x, int y, int z, Direction facing) {
        static Thing of(PlaceholderKind kind, int x, int y, int z, Direction facing) {
            return new Thing(Optional.of(kind), x, y, z, facing);
        }

        static Thing spawn(int x, int z) {
            return new Thing(Optional.empty(), x, 1, z, Direction.NORTH);
        }
    }

    static final List<Sample> SAMPLES = List.of(
            new Sample("start", 11, 11, List.of("start"), 1, Optional.empty(), all(Connector.Kind.DOOR),
                    List.of(Thing.of(PlaceholderKind.WALL_BUY, 2, 2, 1, Direction.SOUTH), Thing.spawn(9, 9))),
            new Sample("hall", 5, 11, List.of("hallway"), 4, Optional.empty(),
                    Map.of(Direction.NORTH, Connector.Kind.OPEN, Direction.SOUTH, Connector.Kind.OPEN), List.of(Thing.spawn(1, 5))),
            new Sample("room", 11, 11, List.of(), 4, Optional.empty(), all(Connector.Kind.DOOR),
                    List.of(Thing.of(PlaceholderKind.MYSTERY_BOX, 2, 1, 8, Direction.EAST), Thing.of(PlaceholderKind.WALL_BUY, 8, 2, 1, Direction.SOUTH),
                            Thing.spawn(9, 9))),
            new Sample("perks", 11, 11, List.of("perks"), 2, Optional.empty(),
                    Map.of(Direction.NORTH, Connector.Kind.DOOR, Direction.SOUTH, Connector.Kind.DOOR),
                    List.of(Thing.of(PlaceholderKind.VENDING_MACHINE, 1, 1, 3, Direction.EAST), Thing.of(PlaceholderKind.VENDING_MACHINE, 9, 1, 6, Direction.WEST),
                            Thing.spawn(2, 9))),
            new Sample("pack_a_punch", 9, 9, List.of("pack_a_punch"), 1, Optional.empty(), Map.of(Direction.SOUTH, Connector.Kind.DOOR),
                    List.of(Thing.of(PlaceholderKind.UPGRADER, 4, 1, 2, Direction.SOUTH), Thing.spawn(6, 6))),
            new Sample("power", 9, 9, List.of("power"), 1, Optional.of(1), Map.of(Direction.SOUTH, Connector.Kind.DOOR),
                    List.of(Thing.of(PlaceholderKind.POWER_SWITCH, 4, 1, 3, Direction.SOUTH), Thing.spawn(6, 6))));

    private static Map<Direction, Connector.Kind> all(Connector.Kind kind) {
        return Map.of(Direction.NORTH, kind, Direction.EAST, kind, Direction.SOUTH, kind, Direction.WEST, kind);
    }

    /** The starter rules for the workshop pack: 8 to 14 rooms, one upgrader 3 or more rooms in, the power 1 to 4 in, and the machines. */
    public static GenerationRules rules() {
        List<PoolEntry> cans = List.of("hellstorm:vigoraid", "hellstorm:load_up", "hellstorm:saints_brew", "hellstorm:off_hand",
                "hellstorm:jockey_energy", "hellstorm:hellfire").stream().map(id -> new PoolEntry(id, 1, Depths.ANY, Optional.empty())).toList();
        List<PoolEntry> guns = List.of(
                new PoolEntry("tacz:glock_17", 2, new Depths(0, Optional.of(1)), Optional.empty()),
                new PoolEntry("tacz:m1911", 2, new Depths(0, Optional.of(2)), Optional.empty()),
                new PoolEntry("tacz:hk_mp5a5", 2, new Depths(1, Optional.empty()), Optional.empty()),
                new PoolEntry("tacz:m870", 1, new Depths(2, Optional.empty()), Optional.empty()),
                new PoolEntry("tacz:ak47", 1, new Depths(3, Optional.empty()), Optional.empty()),
                new PoolEntry("tacz:m4a1", 1, new Depths(3, Optional.empty()), Optional.empty()));
        return new GenerationRules(new Range(8, 14), "start",
                List.of(new TagSpec("pack_a_punch", 1, Optional.of(1), new Depths(3, Optional.empty())),
                        new TagSpec("power", 1, Optional.of(1), new Depths(1, Optional.of(4))),
                        new TagSpec("perks", 0, Optional.of(2), Depths.ANY)),
                Map.of(PlaceholderKind.VENDING_MACHINE, new FeatureRule(Optional.of(new Range(2, 4)), Depths.ANY, cans, true),
                        PlaceholderKind.WALL_BUY, new FeatureRule(Optional.empty(), Depths.ANY, guns, false),
                        PlaceholderKind.MYSTERY_BOX, new FeatureRule(Optional.of(new Range(1, 3)), Depths.ANY, List.of(), false),
                        PlaceholderKind.UPGRADER, new FeatureRule(Optional.of(new Range(1, 1)), Depths.ANY, List.of(), false),
                        PlaceholderKind.POWER_SWITCH, new FeatureRule(Optional.of(new Range(1, 1)), Depths.ANY, List.of(), false)),
                GenerationRules.DoorRule.DEFAULT, GenerationRules.DEFAULT_SEAL);
    }

    private Workshop() {}

    /**
     * Builds the boilerplate with its first room's corner at {@code origin}, the rooms in a row along +x, and writes the
     * workshop pack's starter {@code generation.json} into {@code packs} if the pack has none. Returns each room's Room Block.
     */
    public static List<BlockPos> build(ServerLevel level, BlockPos origin, Path packs) {
        int length = SAMPLES.stream().mapToInt(s -> s.sx() + GAP).sum();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-GAP, -1, -GAP), origin.offset(length, -1, 11 + GAP))) {
            level.setBlock(pos, floor, Block.UPDATE_CLIENTS);
        }
        List<BlockPos> roomBlocks = new ArrayList<>();
        int x = 0;
        for (Sample sample : SAMPLES) {
            BlockPos corner = origin.offset(x, 0, 0);
            build(level, corner, sample);
            BlockPos roomBlock = corner.offset(0, 0, -3);
            level.setBlock(roomBlock, GenRegistries.ROOM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(roomBlock) instanceof RoomBlock.Entity entity) {
                entity.setConfig(new RoomBlock.Config(PACK, sample.id(), sample.tags(), sample.weight(), sample.max(),
                        corner.subtract(roomBlock), new Vec3i(sample.sx(), HEIGHT, sample.sz())));
            }
            roomBlocks.add(roomBlock);
            x += sample.sx() + GAP;
        }
        MapModes.set(level, MapMode.EDIT);
        Path rules = packs.resolve(PACK).resolve("generation.json");
        if (!Files.exists(rules)) {
            try {
                Files.createDirectories(rules.getParent());
                RoomPack.writeRules(packs, PACK, rules());
            } catch (IOException e) {
                Hellstorm.LOG.warn("Couldn't write the workshop pack's generation.json", e);
            }
        }
        return roomBlocks;
    }

    /** One brick room: floor and walls, connectors in its openings, its placeholders and spawn marker, and its zone. */
    static void build(ServerLevel level, BlockPos corner, Sample s) {
        BlockState brick = Blocks.BRICKS.defaultBlockState();
        for (int dx = 0; dx < s.sx(); dx++) {
            for (int dz = 0; dz < s.sz(); dz++) {
                for (int dy = 0; dy < HEIGHT; dy++) {
                    boolean wall = dx == 0 || dz == 0 || dx == s.sx() - 1 || dz == s.sz() - 1;
                    level.setBlock(corner.offset(dx, dy, dz), dy == 0 || wall ? brick : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        s.openings().forEach((side, kind) -> {
            for (BlockPos pos : opening(corner, s, side)) {
                level.setBlock(pos, GenRegistries.CONNECTOR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, side), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof ConnectorBlock.Entity entity) {
                    entity.setConfig(new ConnectorBlock.Config(kind, ""));
                }
            }
        });
        for (Thing thing : s.things()) {
            BlockPos pos = corner.offset(thing.x(), thing.y(), thing.z());
            BlockState state = thing.kind().map(k -> GenRegistries.PLACEHOLDERS.get(k).get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, thing.facing())).orElseGet(() -> PapRegistries.SPAWN_MARKER.get().defaultBlockState());
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        ZoneId zone = new ZoneId(s.id());
        BlockBox inside = BlockBox.of(corner.offset(1, 1, 1), corner.offset(s.sx() - 2, HEIGHT - 1, s.sz() - 2));
        MapEdits.apply(level, map -> map.withZone(new Zone(zone, List.of(inside), s.tags().contains("start"))));
    }

    /** A side's 3 by 3 opening, in the middle of the wall. */
    static List<BlockPos> opening(BlockPos corner, Sample s, Direction side) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int y = 1; y <= 3; y++) {
                out.add(switch (side) {
                    case NORTH -> corner.offset(s.sx() / 2 + i, y, 0);
                    case SOUTH -> corner.offset(s.sx() / 2 + i, y, s.sz() - 1);
                    case WEST -> corner.offset(0, y, s.sz() / 2 + i);
                    default -> corner.offset(s.sx() - 1, y, s.sz() / 2 + i);
                });
            }
        }
        return out;
    }
}
