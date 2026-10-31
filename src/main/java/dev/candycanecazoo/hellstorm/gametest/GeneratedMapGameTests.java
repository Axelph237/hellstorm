package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.gen.block.ConnectorBlock;
import dev.candycanecazoo.hellstorm.gen.block.PlaceholderBlock;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.gen.layout.Layout;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.gen.place.MapGenerator;
import dev.candycanecazoo.hellstorm.gen.place.RoomSaver;
import dev.candycanecazoo.hellstorm.gen.world.Workshop;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The whole chain for generated maps on a real server: the workshop's boilerplate built, every room saved by its Room
 * Block into a pack, and a map generated from that pack that validates as playable, with its machines made from the
 * placeholders, no placeholder or connector left over, doors between rooms, and the same layout from the same seed.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratedMapGameTests {
    private GeneratedMapGameTests() {}

    @GameTest(template = "floor", batch = "generated_map", timeoutTicks = 400)
    public static void theWorkshopsRoomsSaveIntoAPackThatGeneratesAPlayableMap(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        MapMode modeBefore = MapSavedData.get(level).mode();
        Path packs = Files.createTempDirectory("hellstorm-packs");
        try {
            MapEdits.apply(level, m -> ZombiesMap.EMPTY);
            BlockPos workshop = new BlockPos(6000, 80, 6000);
            List<BlockPos> roomBlocks = Workshop.build(level, workshop, packs);
            helper.assertTrue(roomBlocks.size() == 6, "the workshop has six rooms, not " + roomBlocks.size());
            for (BlockPos pos : roomBlocks) {
                RoomBlock.Entity block = (RoomBlock.Entity) level.getBlockEntity(pos);
                RoomSaver.Result saved = RoomSaver.save(level, block.config(), block.min(), packs);
                helper.assertTrue(saved.saved(), "the room at " + pos.toShortString() + " should save: " + saved.message());
                helper.assertTrue(saved.warnings().isEmpty(), "and with no warnings: " + saved.warnings());
            }
            RoomPack pack = RoomPack.read(packs, Workshop.PACK);
            helper.assertTrue(pack.rooms().size() == 6, "the pack has six rooms, not " + pack.rooms().keySet());
            helper.assertTrue(pack.rules().equals(Workshop.rules()), "and the workshop's starter rules");

            MapEdits.apply(level, m -> ZombiesMap.EMPTY);
            BlockPos origin = new BlockPos(7000, 80, 7000);
            MapGenerator.Result result = MapGenerator.generate(level, List.of(pack), 12345L, origin);
            MapGenerator.Result.Generated made = assertGenerated(helper, result);
            ZombiesMap map = made.map();
            Layout layout = made.layout();
            Hellstorm.LOG.info("Generated map: {} rooms, {} doors, {} zones, features {}, problems {}", layout.rooms().size(), map.doors().size(),
                    map.zones().size(), map.features().values().stream().collect(Collectors.groupingBy(k -> k, Collectors.counting())), made.report().problems());
            helper.assertTrue(made.report().playable(), "the generated map should be playable: " + made.report().problems());
            helper.assertTrue(layout.rooms().size() >= 8 && layout.rooms().size() <= 14, "8 to 14 rooms, not " + layout.rooms().size());
            helper.assertTrue(map.zones().size() == layout.rooms().size(), "a zone per room: " + map.zones().size());
            helper.assertTrue(map.startZones().size() == 1, "one start zone, the start room's: " + map.startZones());
            helper.assertTrue(map.features(FeatureKind.PAP_MACHINE).size() == 1, "one upgrader: " + map.features(FeatureKind.PAP_MACHINE));
            helper.assertTrue(map.features(FeatureKind.POWER_SWITCH).size() == 1, "one power switch");
            int vending = map.features(FeatureKind.VENDING_MACHINE).size();
            helper.assertTrue(vending >= 2 && vending <= 4, "two to four vending machines, not " + vending);
            int boxes = map.features(FeatureKind.MYSTERY_BOX).size();
            helper.assertTrue(boxes >= 1 && boxes <= 3, "one to three crate locations, not " + boxes);
            helper.assertTrue(!map.doors().isEmpty(), "doors between rooms");
            helper.assertTrue(map.features(FeatureKind.SPAWN).size() >= layout.rooms().size(), "a spawn marker in every room");
            BlockBox area = layout.rooms().stream().map(r -> {
                var b = r.box();
                return BlockBox.of(origin.offset(b.minX(), b.minY(), b.minZ()), origin.offset(b.maxX(), b.maxY(), b.maxZ()));
            }).reduce(BlockBox::union).orElseThrow();
            List<BlockPos> leftOver = BlockPos.betweenClosedStream(area.min(), area.max())
                    .filter(p -> level.getBlockState(p).getBlock() instanceof PlaceholderBlock || level.getBlockState(p).getBlock() instanceof ConnectorBlock)
                    .map(BlockPos::immutable).limit(5).collect(Collectors.toList());
            helper.assertTrue(leftOver.isEmpty(), "no placeholder or connector is left in the map: " + leftOver);

            MapPack.clear(level, map);
            MapEdits.apply(level, m -> ZombiesMap.EMPTY);
            MapGenerator.Result again = MapGenerator.generate(level, List.of(pack), 12345L, new BlockPos(8000, 80, 8000));
            helper.assertTrue(assertGenerated(helper, again).layout().equals(layout), "the same seed gives the same layout");
            MapPack.clear(level, ((MapGenerator.Result.Generated) again).map());
        } finally {
            MapEdits.apply(level, m -> before);
            MapModes.set(level, modeBefore);
        }
        helper.succeed();
    }

    private static MapGenerator.Result.Generated assertGenerated(GameTestHelper helper, MapGenerator.Result result) {
        if (result instanceof MapGenerator.Result.Failed failed) {
            helper.fail("generation failed: " + failed.reason());
        }
        return (MapGenerator.Result.Generated) result;
    }
}
