package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.MapGeometry;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** A map exported and imported somewhere else keeps its blocks, its machines' settings, and its zones, all moved together. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapPackGameTests {
    private static final int SHIFT = 5;

    private MapPackGameTests() {}

    @GameTest(template = "floor", batch = "map_pack")
    public static void anExportedMapImportsElsewhereWithItsBlocksMachinesAndZones(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        ZombiesMap before = MapSavedData.get(level).map();
        BlockPos stone = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        BlockPos wallBuy = helper.absolutePos(new BlockPos(2, ROOM_Y, 2));
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wallBuy, PapRegistries.WALL_BUY.get().defaultBlockState().setValue(WallBuyBlock.FACING, Direction.SOUTH));
        WallBuy.Config sells = WallBuy.Config.forItem(new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 3, Optional.empty()),
                777, WallBuy.Repurchase.TOP_UP);
        ((WallBuyBlock.Entity) level.getBlockEntity(wallBuy)).setConfig(sells);
        BlockBox area = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 1, 3)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_pack"), List.of(area), true)).withFeature(wallBuy, FeatureKind.WALL_BUY);
        Path file = Files.createTempDirectory("hellstorm-pack").resolve("gametest.zip");
        try {
            MapPack.export(level, "gametest", area, map, Optional.empty(), file);
            MapPack.Contents contents = MapPack.read(file);
            helper.assertTrue(contents.manifest().origin().equals(area.min()), "the pack should remember where it came from");
            MapPack.place(level, contents, area.min().east(SHIFT));

            helper.assertTrue(level.getBlockState(stone.east(SHIFT)).is(Blocks.STONE), "the stone wasn't copied");
            helper.assertTrue(level.getBlockState(wallBuy.east(SHIFT)).getValue(WallBuyBlock.FACING) == Direction.SOUTH, "the wall-buy lost its facing");
            helper.assertTrue(level.getBlockEntity(wallBuy.east(SHIFT)) instanceof WallBuyBlock.Entity copy && copy.config().equals(sells),
                    "the wall-buy's settings didn't come along");
            helper.assertTrue(MapSavedData.get(level).map().equals(MapGeometry.moved(map, SHIFT, 0, 0)), "the map should be the same map, moved: "
                    + MapSavedData.get(level).map());
        } finally {
            MapEdits.apply(level, m -> before);
            Files.deleteIfExists(file);
        }
        helper.succeed();
    }

    /**
     * Importing with {@code replace} first takes out the old map's own blocks: a wall-buy at one of its feature positions
     * and the barricades in its window go, with no drops, and the terrain around them stays.
     */
    @GameTest(template = "floor", batch = "map_pack_clear")
    public static void clearingAMapTakesOutItsMachinesAndBarricadesButNotTheTerrain(GameTestHelper helper) {
        var level = helper.getLevel();
        ZombiesMap saved = MapSavedData.get(level).map();
        try {
            BlockPos machine = helper.absolutePos(new BlockPos(2, TestLayout.ROOM_Y, 2));
            level.setBlockAndUpdate(machine, PapRegistries.WALL_BUY.get().defaultBlockState()
                    .setValue(dev.candycanecazoo.hellstorm.feature.WallBuyBlock.FACING, net.minecraft.core.Direction.NORTH));
            BlockPos board = helper.absolutePos(new BlockPos(6, TestLayout.ROOM_Y, 6));
            level.setBlockAndUpdate(board, PapRegistries.BARRICADE.get().defaultBlockState());
            BlockPos wall = helper.absolutePos(new BlockPos(6, TestLayout.ROOM_Y + 1, 6));
            level.setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            ZombiesMap old = ZombiesMap.EMPTY.withFeature(machine, FeatureKind.WALL_BUY)
                    .withWindow(new dev.candycanecazoo.hellstorm.map.WindowDef("old", BlockBox.of(board, board), net.minecraft.core.Direction.NORTH, 6));
            MapPack.Cleared cleared = MapPack.clear(level, old);
            helper.assertTrue(cleared.machines() == 1 && cleared.windows() == 1, "cleared " + cleared);
            helper.assertTrue(level.getBlockState(machine).isAir(), "the old wall-buy is gone");
            helper.assertTrue(level.getBlockState(board).isAir(), "the old window's barricade is gone");
            helper.assertTrue(level.getBlockState(wall).is(net.minecraft.world.level.block.Blocks.STONE), "terrain stays");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(machine).inflate(3)).isEmpty(), "nothing dropped");
        } finally {
            MapEdits.apply(level, m -> saved);
        }
        helper.succeed();
    }
}
