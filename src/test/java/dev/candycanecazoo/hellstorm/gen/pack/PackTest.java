package dev.candycanecazoo.hellstorm.gen.pack;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.gen.layout.Facing;
import dev.candycanecazoo.hellstorm.gen.layout.Size;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.Depths;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.FeatureRule;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.PoolEntry;
import dev.candycanecazoo.hellstorm.gen.pack.GenerationRules.Range;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class PackTest {
    private static final RoomDef ROOM = new RoomDef("hall", new Size(5, 6, 11), Set.of("hallway"), 3, Optional.empty(),
            List.of(new Connector("c0", new Box(1, 1, 0, 3, 3, 0), Facing.NORTH, Connector.Kind.OPEN, "")),
            List.of(new RoomDef.Placeholder(PlaceholderKind.WALL_BUY, 1, 2, 5, Facing.EAST)),
            ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("hall"), List.of(new BlockBox(1, 1, 1, 3, 5, 9)), false)));

    @Test
    void aRoomAndTheRulesReadBackAsWritten() {
        var json = RoomDef.CODEC.encodeStart(JsonOps.INSTANCE, ROOM).getOrThrow(false, e -> {});
        assertEquals(ROOM, RoomDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, e -> {}));
        GenerationRules rules = dev.candycanecazoo.hellstorm.gen.world.Workshop.rules();
        var rulesJson = GenerationRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow(false, e -> {});
        assertEquals(rules, GenerationRules.CODEC.parse(JsonOps.INSTANCE, rulesJson).getOrThrow(false, e -> {}));
    }

    @Test
    void anEmptyGenerationFileIsTheDefaults() {
        assertEquals(GenerationRules.DEFAULT, GenerationRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow(false, e -> {}));
        assertEquals(FeatureRule.EVERY, GenerationRules.DEFAULT.feature(PlaceholderKind.MYSTERY_BOX), "a kind with no rule keeps every placeholder");
    }

    @Test
    void aSavedRoomReadsBackInItsPack() throws Exception {
        Path folder = Files.createTempDirectory("hellstorm-pack-test");
        CompoundTag template = new CompoundTag();
        template.putInt("DataVersion", 3465);
        RoomPack.writeRoom(folder, "mine", ROOM, template);
        assertEquals(List.of("mine"), RoomPack.available(folder));
        RoomPack pack = RoomPack.read(folder, "mine");
        assertEquals(ROOM, pack.rooms().get("hall").def());
        assertEquals(template, pack.rooms().get("hall").template());
        assertEquals(GenerationRules.DEFAULT, pack.rules(), "a new pack starts with the default rules");
        assertEquals("mine:hall", pack.pieceId("hall"));
    }

    @Test
    void aPacksDatapackIsKeptForInstallingAsItsOwnFolder() throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.json", "{\"format\": 2, \"name\": \"x\"}".getBytes(StandardCharsets.UTF_8));
        files.put("datapack/pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
        files.put("datapack/data/x/hellstorm/scripts/announcer.lua", "-- hello".getBytes(StandardCharsets.UTF_8));
        RoomPack pack = RoomPack.parse("x", files);
        assertEquals(Set.of("pack.mcmeta", "data/x/hellstorm/scripts/announcer.lua"), pack.datapack().keySet());
        assertEquals("-- hello", new String(pack.datapack().get("data/x/hellstorm/scripts/announcer.lua"), StandardCharsets.UTF_8));
    }

    @Test
    void aPackWithStrangeOrMissingFilesIsRefused() {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.json", "{\"format\": 2, \"name\": \"x\"}".getBytes(StandardCharsets.UTF_8));
        files.put("../evil.txt", new byte[0]);
        assertThrows(RoomPack.BadPack.class, () -> RoomPack.parse("x", files));
        files.remove("../evil.txt");
        files.put("rooms/a.json", "{\"id\": \"a\", \"size\": [3, 3, 3]}".getBytes(StandardCharsets.UTF_8));
        assertThrows(RoomPack.BadPack.class, () -> RoomPack.parse("x", files), "a room without its blocks");
        files.remove("rooms/a.json");
        files.put("pack.json", "{\"format\": 1, \"name\": \"x\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(RoomPack.BadPack.class, () -> RoomPack.parse("x", files), "a map pack, not a room pack");
    }

    private static List<FeatureResolver.Found> found(PlaceholderKind kind, int... depths) {
        List<FeatureResolver.Found> out = new ArrayList<>();
        for (int i = 0; i < depths.length; i++) {
            out.add(new FeatureResolver.Found(i, depths[i], new RoomDef.Placeholder(kind, i, 1, 1, Facing.NORTH)));
        }
        return out;
    }

    private static GenerationRules rules(PlaceholderKind kind, FeatureRule rule) {
        return new GenerationRules(GenerationRules.DEFAULT.rooms(), "start", List.of(), Map.of(kind, rule), GenerationRules.DoorRule.DEFAULT, "minecraft:bricks");
    }

    @Test
    void withoutACountEveryPlaceholderAtTheAllowedDepthsBecomesItsMachine() {
        var rule = new FeatureRule(Optional.empty(), new Depths(1, Optional.of(3)), List.of(), false);
        var resolution = FeatureResolver.resolve(found(PlaceholderKind.MYSTERY_BOX, 0, 1, 2, 3, 4), rules(PlaceholderKind.MYSTERY_BOX, rule), new SplittableRandom(1));
        assertTrue(resolution.ok());
        assertEquals(List.of(1, 2, 3), resolution.choices().stream().filter(FeatureResolver.Choice::machine).map(c -> c.found().depth()).sorted().toList());
    }

    @Test
    void aCountPicksThatManyAndTooFewIsAProblem() {
        var two = new FeatureRule(Optional.of(new Range(2, 2)), Depths.ANY, List.of(), false);
        for (long seed = 0; seed < 20; seed++) {
            var resolution = FeatureResolver.resolve(found(PlaceholderKind.UPGRADER, 1, 2, 3, 4), rules(PlaceholderKind.UPGRADER, two), new SplittableRandom(seed));
            assertEquals(2, resolution.choices().stream().filter(FeatureResolver.Choice::machine).count());
            assertEquals(4, resolution.choices().size(), "the rest are taken out");
        }
        var three = new FeatureRule(Optional.of(new Range(3, 3)), Depths.ANY, List.of(), false);
        assertFalse(FeatureResolver.resolve(found(PlaceholderKind.UPGRADER, 1, 2), rules(PlaceholderKind.UPGRADER, three), new SplittableRandom(1)).ok());
    }

    @Test
    void aUniquePoolGivesEachEntryOnceAndEntriesKeepToTheirDepths() {
        List<PoolEntry> pool = List.of(new PoolEntry("a", 1, Depths.ANY, Optional.empty()), new PoolEntry("b", 1, Depths.ANY, Optional.empty()),
                new PoolEntry("deep", 1, new Depths(5, Optional.empty()), Optional.empty()));
        var unique = new FeatureRule(Optional.empty(), Depths.ANY, pool, true);
        var resolution = FeatureResolver.resolve(found(PlaceholderKind.VENDING_MACHINE, 1, 2), rules(PlaceholderKind.VENDING_MACHINE, unique), new SplittableRandom(3));
        assertEquals(Set.of("a", "b"), Set.copyOf(resolution.choices().stream().map(c -> c.entry().orElseThrow().item()).toList()));
        var tooMany = FeatureResolver.resolve(found(PlaceholderKind.VENDING_MACHINE, 1, 2, 3), rules(PlaceholderKind.VENDING_MACHINE, unique), new SplittableRandom(3));
        assertFalse(tooMany.ok(), "three machines can't each have a different one of the two shallow entries");
    }

    @Test
    void theSameSeedDecidesTheSameWay() {
        var rule = new FeatureRule(Optional.of(new Range(1, 3)), Depths.ANY, List.of(), false);
        var a = FeatureResolver.resolve(found(PlaceholderKind.MYSTERY_BOX, 1, 2, 3, 4, 5), rules(PlaceholderKind.MYSTERY_BOX, rule), new SplittableRandom(9));
        var b = FeatureResolver.resolve(found(PlaceholderKind.MYSTERY_BOX, 1, 2, 3, 4, 5), rules(PlaceholderKind.MYSTERY_BOX, rule), new SplittableRandom(9));
        assertEquals(a, b);
    }

    @Test
    void doorsCostMoreTheDeeperTheyAre() {
        var doors = new GenerationRules.DoorRule(750, 250, "minecraft:oak_planks", Optional.empty());
        assertEquals(750, doors.cost(1));
        assertEquals(1250, doors.cost(3));
    }
}
