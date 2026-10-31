package dev.candycanecazoo.hellstorm.gen.place;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.data.PurchaseDefaults;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.gen.layout.Layout;
import dev.candycanecazoo.hellstorm.gen.layout.LayoutGenerator;
import dev.candycanecazoo.hellstorm.gen.layout.Piece;
import dev.candycanecazoo.hellstorm.gen.pack.FeatureResolver;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules;
import dev.candycanecazoo.hellstorm.gen.pack.RoomDef;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.validate.MapValidator;
import dev.candycanecazoo.hellstorm.map.validate.ValidationReport;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Generates a map from packs into a level, with its corner at {@code origin}. The rooms of every pack go into one pool;
 * the first pack's {@code generation.json} rules the map. A layout is grown ({@link LayoutGenerator}) and its
 * placeholders decided ({@link FeatureResolver}); a layout whose placeholders can't meet the rules is passed over for the
 * next. Then each room's blocks go in turned, every joined opening is carved through or made a door (costing more the
 * deeper it is), every opening nothing joined is walled, the placeholders become their machines or go, and the rooms'
 * slices of the map, turned and moved, become the level's map in play mode, checked by the validator. Each pack's
 * datapack is installed into the world and the server reloads. The same packs, rules and seed make the same map.
 */
public final class MapGenerator {
    /** Layouts tried for one whose placeholders meet the rules. */
    static final int LAYOUTS = 64;
    private static final long LAYOUT_STEP = 0x632BE59BD9B4E019L;
    private static final long FEATURE_SALT = 0x5DEECE66DL;

    /** What a generation made: the map, how it validated, and notes for whoever asked. Or why nothing was made. */
    public sealed interface Result {
        record Generated(ZombiesMap map, Layout layout, ValidationReport report, List<String> notes) implements Result {}

        record Failed(String reason) implements Result {}
    }

    /** A placed room's pack and room, by the piece id the layout knows it by. */
    private record Source(RoomPack pack, RoomPack.Room room) {}

    private MapGenerator() {}

    /** The packs {@code ids} from {@code hellstorm/maps}; all of them if {@code ids} is empty. */
    public static List<RoomPack> load(List<String> ids) throws IOException, RoomPack.BadPack {
        List<String> wanted = ids.isEmpty() ? RoomPack.available(RoomSaver.packs()) : ids;
        List<RoomPack> packs = new ArrayList<>();
        for (String id : wanted) {
            packs.add(RoomPack.read(RoomSaver.packs(), id));
        }
        return packs;
    }

    public static Result generate(ServerLevel level, List<RoomPack> packs, long seed, BlockPos origin) {
        if (packs.isEmpty()) {
            return new Result.Failed("No room packs to generate from: save rooms with a Room Block, or put packs in hellstorm/maps");
        }
        GenerationRules rules = packs.get(0).rules();
        Map<String, Source> sources = new HashMap<>();
        List<Piece> pieces = new ArrayList<>();
        for (RoomPack pack : packs) {
            for (RoomPack.Room room : pack.rooms().values()) {
                String id = pack.pieceId(room.def().id());
                sources.put(id, new Source(pack, room));
                pieces.add(room.def().piece(id));
            }
        }
        String lastProblem = "";
        for (int attempt = 0; attempt < LAYOUTS; attempt++) {
            long layoutSeed = seed + attempt * LAYOUT_STEP;
            LayoutGenerator.Result laid = LayoutGenerator.generate(pieces, rules.layoutRules(), layoutSeed);
            if (laid instanceof LayoutGenerator.Result.Failed failed) {
                return new Result.Failed(failed.reason());
            }
            Layout layout = ((LayoutGenerator.Result.Generated) laid).layout();
            List<FeatureResolver.Found> found = new ArrayList<>();
            for (Layout.PlacedRoom room : layout.rooms()) {
                for (RoomDef.Placeholder p : sources.get(room.piece().id()).room().def().placeholders()) {
                    found.add(new FeatureResolver.Found(room.index(), room.depth(), p));
                }
            }
            FeatureResolver.Resolution features = FeatureResolver.resolve(found, rules, new SplittableRandom(layoutSeed ^ FEATURE_SALT));
            if (!features.ok()) {
                lastProblem = String.join("; ", features.problems());
                continue;
            }
            return place(level, packs, rules, sources, layout, features, origin);
        }
        return new Result.Failed("No layout in " + LAYOUTS + " had placeholders that meet the pack's feature rules; the last: " + lastProblem);
    }

    private static Result place(ServerLevel level, List<RoomPack> packs, GenerationRules rules, Map<String, Source> sources, Layout layout,
                                FeatureResolver.Resolution features, BlockPos origin) {
        List<String> notes = new ArrayList<>();
        List<RoomTransform> transforms = new ArrayList<>();
        // The rooms' blocks.
        for (Layout.PlacedRoom room : layout.rooms()) {
            RoomTransform t = new RoomTransform(room.rotation(), room.piece().size(), origin.offset(room.x(), room.y(), room.z()));
            transforms.add(t);
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), sources.get(room.piece().id()).room().template());
            BlockPos at = t.templatePosition();
            template.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(t.minecraftRotation()).setIgnoreEntities(true),
                    level.random, Block.UPDATE_CLIENTS);
        }
        // Openings: carved, made doors, or walled.
        BlockState doorBlock = block(rules.doors().block(), Blocks.OAK_PLANKS);
        BlockState seal = block(rules.seal(), Blocks.BRICKS);
        Map<String, DoorDef> doors = new LinkedHashMap<>();
        Map<Integer, java.util.Set<String>> joined = new HashMap<>();
        for (Layout.Joint joint : layout.joints()) {
            joined.computeIfAbsent(joint.roomA(), k -> new java.util.HashSet<>()).add(joint.connectorA());
            joined.computeIfAbsent(joint.roomB(), k -> new java.util.HashSet<>()).add(joint.connectorB());
            List<BlockPos> blocks = new ArrayList<>();
            blocks.addAll(openingBlocks(transforms.get(joint.roomA()), connector(layout, joint.roomA(), joint.connectorA())));
            blocks.addAll(openingBlocks(transforms.get(joint.roomB()), connector(layout, joint.roomB(), joint.connectorB())));
            if (joint.kind() == Connector.Kind.OPEN) {
                blocks.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
            } else {
                int depth = Math.max(layout.rooms().get(joint.roomA()).depth(), layout.rooms().get(joint.roomB()).depth());
                blocks.forEach(pos -> level.setBlock(pos, doorBlock, Block.UPDATE_CLIENTS));
                String id = "door_" + doors.size();
                boolean power = rules.doors().powerFromDepth().map(d -> depth >= d).orElse(false);
                doors.put(id, new DoorDef(id, java.util.Set.copyOf(blocks), rules.doors().cost(depth), power, DoorDef.Style.DOOR));
            }
        }
        for (Layout.PlacedRoom room : layout.rooms()) {
            for (Connector c : room.piece().connectors()) {
                if (!joined.getOrDefault(room.index(), java.util.Set.of()).contains(c.id())) {
                    openingBlocks(transforms.get(room.index()), c).forEach(pos -> level.setBlock(pos, seal, Block.UPDATE_CLIENTS));
                }
            }
        }
        // The map: every room's slice, turned and moved, then the doors between rooms. Features come from the level,
        // where each machine and spawn marker registered itself as it was placed.
        ZombiesMap map = ZombiesMap.EMPTY;
        for (Layout.PlacedRoom room : layout.rooms()) {
            ZombiesMap slice = transforms.get(room.index()).apply(sources.get(room.piece().id()).room().def().slice(), "r" + room.index(), room.index() == 0);
            map = merge(map, slice);
        }
        for (DoorDef door : doors.values()) {
            map = map.withDoor(door);
        }
        ZombiesMap rooms = map;
        MapEdits.apply(level, old -> {
            ZombiesMap out = rooms;
            for (Map.Entry<BlockPos, dev.candycanecazoo.hellstorm.map.FeatureKind> f : old.features().entrySet()) {
                out = out.withFeature(f.getKey(), f.getValue());
            }
            return out;
        });
        // Placeholders: their machines, or nothing.
        for (FeatureResolver.Choice choice : features.choices()) {
            RoomTransform t = transforms.get(choice.found().room());
            RoomDef.Placeholder p = choice.found().placeholder();
            BlockPos pos = t.apply(p.x(), p.y(), p.z());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (choice.machine()) {
                machine(level, pos, t.apply(p.facing()), choice, notes);
            }
        }
        ZombiesMap placed = MapSavedData.get(level).map();
        placed.windows().values().forEach(w -> WindowOps.setBoards(level, w, w.maxBoards()));
        MapModes.set(level, MapMode.PLAY);
        installDatapacks(level, packs, notes);
        ZombiesMap finalMap = MapSavedData.get(level).map();
        ValidationReport report = MapValidator.validate(finalMap, GameManager.validationContext(level));
        return new Result.Generated(finalMap, layout, report, notes);
    }

    private static Connector connector(Layout layout, int room, String id) {
        return layout.rooms().get(room).piece().connectors().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<BlockPos> openingBlocks(RoomTransform t, Connector c) {
        BlockBox box = t.apply(c.opening());
        List<BlockPos> out = new ArrayList<>();
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(p -> out.add(p.immutable()));
        return out;
    }

    private static ZombiesMap merge(ZombiesMap a, ZombiesMap b) {
        ZombiesMap out = a;
        for (var zone : b.zones().values()) {
            out = out.withZone(zone);
        }
        for (var f : b.features().entrySet()) {
            out = out.withFeature(f.getKey(), f.getValue());
        }
        for (var w : b.windows().values()) {
            out = out.withWindow(w);
        }
        for (var d : b.doors().values()) {
            out = out.withDoor(d);
        }
        return out;
    }

    private static BlockState block(String id, Block fallback) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        return (block == null || block == Blocks.AIR ? fallback : block).defaultBlockState();
    }

    /** Builds the machine a chosen placeholder stands for, facing {@code facing}, set from its pool entry if it has one. */
    private static void machine(ServerLevel level, BlockPos pos, Direction facing, FeatureResolver.Choice choice, List<String> notes) {
        Optional<GenerationRules.PoolEntry> entry = choice.entry();
        switch (choice.found().placeholder().kind()) {
            case WALL_BUY -> {
                level.setBlock(pos, PapRegistries.WALL_BUY.get().defaultBlockState().setValue(WallBuyBlock.FACING, facing), Block.UPDATE_ALL);
                entry.flatMap(e -> item(e.item(), true, notes)).ifPresent(spec -> WallBuyBlock.entity(level, pos).ifPresent(wall -> {
                    WallBuy.Config config = PurchaseDefaults.of(spec).map(d -> WallBuy.Config.DEFAULT.selling(spec, d))
                            .orElseGet(() -> WallBuy.Config.forItem(spec, WallBuy.Config.DEFAULT.cost(), WallBuy.Repurchase.defaultFor(spec)));
                    int cost = entry.get().cost().orElse(config.cost());
                    wall.setConfig(new WallBuy.Config(spec, cost, config.repurchase(), config.ammoCost(), config.papAmmoCost(), config.requiresPower()));
                }));
            }
            case VENDING_MACHINE -> {
                VendingMachineBlock.place(level, pos, facing);
                entry.flatMap(e -> item(e.item(), false, notes)).ifPresent(spec -> WallBuyBlock.entity(level, pos).ifPresent(machine -> {
                    int cost = entry.get().cost().orElseGet(() -> PurchaseDefaults.of(spec).map(d -> d.cost()).orElse(VendingMachineBlock.DEFAULT_COST));
                    machine.setConfig(VendingMachineBlock.sells(spec, cost));
                }));
            }
            case MYSTERY_BOX -> MysteryBoxBlock.place(level, pos, facing);
            case UPGRADER -> PapMachineBlock.place(level, pos, facing);
            case POWER_SWITCH -> level.setBlock(pos, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, facing), Block.UPDATE_ALL);
        }
    }

    /**
     * A pool entry's item. For a wall-buy, an id that isn't an item is taken as a TacZ gun id ({@code tacz:ak47}), since
     * guns aren't items of their own.
     */
    static Optional<ItemSpec> item(String text, boolean gunIfNotAnItem, List<String> notes) {
        int brace = text.indexOf('{');
        ResourceLocation id = ResourceLocation.tryParse(brace < 0 ? text : text.substring(0, brace));
        boolean isItem = id != null && ForgeRegistries.ITEMS.containsKey(id);
        var parsed = gunIfNotAnItem && !isItem ? ItemSpec.Gun.parse(text).map(g -> (ItemSpec) g) : ItemSpec.Plain.parse(text).map(p -> (ItemSpec) p);
        Optional<ItemSpec> spec = parsed.result();
        if (spec.isEmpty()) {
            notes.add("Couldn't read the pool item " + text);
        }
        return spec;
    }

    private static void installDatapacks(ServerLevel level, List<RoomPack> packs, List<String> notes) {
        boolean any = false;
        for (RoomPack pack : packs) {
            if (pack.datapack().isEmpty()) {
                continue;
            }
            try {
                MapPack.installDatapack(level.getServer().getWorldPath(LevelResource.DATAPACK_DIR), pack.id(), pack.datapack());
                any = true;
                notes.add("Installed the datapack of pack " + pack.id());
            } catch (IOException e) {
                notes.add("Couldn't install the datapack of pack " + pack.id() + ": " + e.getMessage());
                Hellstorm.LOG.warn("Couldn't install the datapack of pack {}", pack.id(), e);
            }
        }
        if (any) {
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "reload");
        }
    }
}
