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
}
