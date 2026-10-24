package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/**
 * Zombies crossing from one zone into the next, where the two zones' boxes touch, to a player standing just past the
 * boundary. Each test sends zombies from the far end of the east zone and passes once every one of them has reached the
 * player within its deadline, none having stood still for five seconds on the way. The deadlines are what the game's
 * own pathing manages with some room to spare; the steering it replaced missed the doorway ones in a third to half of runs. The
 * arenas reach past the 9x9 test floor, so each test has a batch to itself.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneBoundaryGameTests {
    private static final int WIDTH = 17;
    private static final int LENGTH = 9;
    private static final int BOUNDARY_X = 8;
    /** Five seconds without getting half a block anywhere, short of the player: stuck, not just queueing at a doorway. */
    private static final int STUCK_LIMIT = 100;

    private ZoneBoundaryGameTests() {}

    private static BlockState openTrapdoor(Direction facing) {
        return Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, facing);
    }

    /**
     * A hall walled in at x 0 and 16 and z 0 and 8, split into a west zone (x 1..7, the start) and an east zone (x 8..15)
     * whose boxes touch. {@code boundary} builds the first column of the east zone, x = 8, at each z and height. The player
     * stands at (3, 3) in the west zone and four zombies start at x = 14, or eight at x = 13 and 14 for a {@code crowd}.
     */
    private static void cross(GameTestHelper helper, String name, boolean crowd, int deadline, BiConsumer<Integer, Integer> boundary) {
        Runnable clear = FarTargetGameTests.build(helper, WIDTH, LENGTH, (x, z) -> x == 0 || x == WIDTH - 1 || z == 0 || z == LENGTH - 1);
        for (int z = 1; z < LENGTH - 1; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                boundary.accept(z, dy);
            }
        }
        BlockBox west = BlockBox.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)), helper.absolutePos(new BlockPos(BOUNDARY_X - 1, ROOM_Y + 2, LENGTH - 2)));
        BlockBox east = BlockBox.of(helper.absolutePos(new BlockPos(BOUNDARY_X, ROOM_Y, 1)), helper.absolutePos(new BlockPos(WIDTH - 2, ROOM_Y + 2, LENGTH - 2)));
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_" + name + "_start"), List.of(west), true))
                .withZone(new Zone(new ZoneId("gametest_" + name + "_hall"), List.of(east), false));
        List<BlockPos> spawns = new ArrayList<>();
        for (int z = 1; z < LENGTH - 1; z += 2) {
            spawns.add(new BlockPos(WIDTH - 3, ROOM_Y, z));
            if (crowd) {
                spawns.add(new BlockPos(WIDTH - 4, ROOM_Y, z));
            }
        }
        chase(helper, name, map, new BlockPos(3, ROOM_Y, 3), spawns, deadline, clear);
    }

    /**
     * Starts a game on {@code map} with the player at {@code standAt}, sends a zombie from each of {@code spawnsAt}, and
     * passes once every zombie has reached the player within {@code deadline} ticks, none having stood still on the way
     * for {@link #STUCK_LIMIT} ticks.
     */
    private static void chase(GameTestHelper helper, String name, ZombiesMap map, BlockPos standAt, List<BlockPos> spawnsAt, int deadline,
                              Runnable clear) {
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        helper.assertTrue(!passages.open().isEmpty(), "the zones should connect");
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_boundary_" + name);
        BlockPos stand = helper.absolutePos(standAt);
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        player.setInvulnerable(true);
        List<Mob> zombies = new ArrayList<>();
        for (BlockPos at : spawnsAt) {
            UUID id = active.world().spawnZombie(helper.absolutePos(at),
                            new GameWorld.ZombieSpec(ResourceLocation.tryBuild(Hellstorm.MOD_ID, "zombie"), 200, 0.23))
                    .orElseThrow(() -> new IllegalStateException("a zombie didn't spawn"));
            zombies.add((Mob) helper.getLevel().getEntity(id));
        }
        int[] arrivedAt = new int[zombies.size()];
        int[] held = new int[zombies.size()];
        int[] longestHold = new int[zombies.size()];
        String[] heldAt = new String[zombies.size()];
        Vec3[] mark = new Vec3[zombies.size()];
        int[] ticks = {0};
        boolean[] ended = {false};
        helper.onEachTick(() -> {
            ticks[0]++;
            player.moveTo(stand.getCenter());
            for (int i = 0; i < zombies.size(); i++) {
                Mob zombie = zombies.get(i);
                if (arrivedAt[i] != 0) {
                    continue;
                }
                if (zombie.distanceTo(player) < 2.5) {
                    arrivedAt[i] = ticks[0];
                } else if (mark[i] == null || zombie.position().distanceToSqr(mark[i]) > 0.25) {
                    mark[i] = zombie.position();
                    held[i] = 0;
                } else if (++held[i] > longestHold[i]) {
                    longestHold[i] = held[i];
                    heldAt[i] = at(helper, zombie);
                }
            }
            if (!ended[0] && Arrays.stream(arrivedAt).allMatch(t -> t > 0)) {
                ended[0] = true;
                int worst = worst(longestHold);
                Hellstorm.LOG.info("Zone boundary {}: zombies reached the player after {} ticks; the longest any stood still on the way was {} ticks{}",
                        name, Arrays.toString(arrivedAt), longestHold[worst], heldAt[worst] == null ? "" : ", at " + heldAt[worst]);
                GameManager.end(active);
                clear.run();
            }
        });
        helper.succeedWhen(() -> {
            List<String> left = new ArrayList<>();
            for (int i = 0; i < zombies.size(); i++) {
                if (arrivedAt[i] == 0) {
                    left.add(at(helper, zombies.get(i)));
                }
            }
            helper.assertTrue(left.isEmpty(), "zombies still on the way after " + ticks[0] + " ticks at " + left);
            int worst = worst(longestHold);
            helper.assertTrue(longestHold[worst] < STUCK_LIMIT, "a zombie stood still for " + longestHold[worst] + " ticks at " + heldAt[worst]
                    + " on its way");
            int last = Arrays.stream(arrivedAt).max().orElse(0);
            helper.assertTrue(last <= deadline, "the last zombie reached the player after " + last + " ticks, over the " + deadline + " allowed");
        });
    }

    private static int worst(int[] holds) {
        int worst = 0;
        for (int i = 1; i < holds.length; i++) {
            worst = holds[i] > holds[worst] ? i : worst;
        }
        return worst;
    }

    private static String at(GameTestHelper helper, Mob mob) {
        Vec3 v = helper.relativeVec(mob.position());
        return String.format("(%.1f %.1f %.1f)", v.x, v.y, v.z);
    }

    /**
     * The start and hall of the Test map: the boundary is a wall of open trapdoors, their panels across the way, with a
     * gap at z = 6 and a trapdoor lining the wall at z = 7, so the straight line to the player runs into the panels.
     */
    @GameTest(template = "floor", timeoutTicks = 900, batch = "boundary_trapdoors")
    public static void zombiesCrossATrapdoorBoundaryIntoTheNextZone(GameTestHelper helper) {
        cross(helper, "trapdoors", false, 200, (z, dy) -> {
            if (dy == 3) {
                helper.setBlock(new BlockPos(BOUNDARY_X, ROOM_Y + dy, z), Blocks.STONE);
            } else if (z < 6) {
                helper.setBlock(new BlockPos(BOUNDARY_X, ROOM_Y + dy, z), openTrapdoor(Direction.WEST));
            } else if (z == 7) {
                for (int x = BOUNDARY_X; x <= BOUNDARY_X + 2; x++) {
                    helper.setBlock(new BlockPos(x, ROOM_Y + dy, z), openTrapdoor(Direction.NORTH));
                }
            }
        });
    }

    /** A stone wall between the zones with a 1x2 doorway at z = 6, as the first column of the hall's box. */
    private static void doorway(GameTestHelper helper, int z, int dy) {
        if (!(z == 6 && dy < 2)) {
            helper.setBlock(new BlockPos(BOUNDARY_X, ROOM_Y + dy, z), Blocks.STONE);
        }
    }

    @GameTest(template = "floor", timeoutTicks = 900, batch = "boundary_doorway")
    public static void zombiesCrossADoorwayBoundaryIntoTheNextZone(GameTestHelper helper) {
        cross(helper, "doorway", false, 200, (z, dy) -> doorway(helper, z, dy));
    }

    /** Eight zombies for the one doorway, so they arrive in a crowd. */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "boundary_doorway_crowd")
    public static void aCrowdOfZombiesCrossesADoorwayBoundaryIntoTheNextZone(GameTestHelper helper) {
        cross(helper, "doorway_crowd", true, 260, (z, dy) -> doorway(helper, z, dy));
    }

    /** Test map coordinates to the test's own: the layout's lowest layer is the floor. */
    private static BlockPos testMap(int x, int y, int z) {
        return new BlockPos(x - TestMapLayout.MIN_X, y - TestMapLayout.MIN_Y + TestLayout.FLOOR_Y, z - TestMapLayout.MIN_Z);
    }

    private static BlockBox testMapBox(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        return BlockBox.of(helper.absolutePos(testMap(x0, y0, z0)), helper.absolutePos(testMap(x1, y1, z1)));
    }

    /**
     * The Test map itself, block for block: its start room, the hall beside it whose boundary is a wall of open
     * trapdoors with a doorway at one end, and the room behind the hall's barrel door, still shut. Four zombies come in
     * at the hall's window and the player waits in the start room.
     */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "boundary_test_map")
    public static void zombiesCrossFromTheHallIntoTheStartRoomOfTheTestMap(GameTestHelper helper) {
        List<List<String>> layers = TestMapLayout.LAYERS;
        net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> blocks = helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK);
        java.util.Map<Character, BlockState> states = new java.util.HashMap<>();
        TestMapLayout.BLOCKS.forEach((c, text) -> {
            try {
                states.put(c, net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(blocks, text, false).blockState());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException(text, e);
            }
        });
        int width = layers.get(0).get(0).length();
        int length = layers.get(0).size();
        Runnable discardMobs = () -> helper.getLevel().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(
                helper.absolutePos(new BlockPos(-1, 0, -1)), helper.absolutePos(new BlockPos(width + 1, layers.size() + 3, length + 1)))).forEach(Mob::discard);
        discardMobs.run();
        // A stone shell round the copy: the test world may be natural terrain, where gravel falls and lava flows into a hollow.
        for (int y = -1; y <= layers.size(); y++) {
            for (int z = -1; z <= length; z++) {
                for (int x = -1; x <= width; x++) {
                    boolean inside = y >= 0 && y < layers.size() && z >= 0 && z < length && x >= 0 && x < width;
                    char c = inside ? layers.get(y).get(z).charAt(x) : '#';
                    helper.setBlock(new BlockPos(x, TestLayout.FLOOR_Y + y, z), c == '#' ? Blocks.STONE.defaultBlockState()
                            : c == '.' ? Blocks.AIR.defaultBlockState() : states.get(c));
                }
            }
        }
        Runnable clear = () -> {
            for (int y = -1; y <= layers.size(); y++) {
                for (int z = -1; z <= length; z++) {
                    for (int x = -1; x <= width; x++) {
                        helper.setBlock(new BlockPos(x, TestLayout.FLOOR_Y + y, z), Blocks.AIR);
                    }
                }
            }
            discardMobs.run();
        };
        dev.candycanecazoo.hellstorm.map.DoorDef door = dev.candycanecazoo.hellstorm.map.DoorDef.empty("door_to_chamber", 500);
        int[][] members = {{14, -60, 9}, {14, -60, 10}, {14, -59, 10}, {14, -58, 10}, {15, -60, 8}, {15, -60, 9}, {15, -59, 9}, {15, -60, 10},
                {15, -59, 10}, {15, -58, 10}, {15, -57, 10}, {16, -60, 9}, {16, -59, 9}, {16, -60, 10}, {16, -59, 10}, {16, -58, 10}};
        for (int[] m : members) {
            door = door.withMember(helper.absolutePos(testMap(m[0], m[1], m[2])));
        }
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_test_map_start"), List.of(testMapBox(helper, -1, -60, -2, 9, -54, 8)), true))
                .withZone(new Zone(new ZoneId("gametest_test_map_hall"), List.of(testMapBox(helper, 10, -60, 3, 17, -57, 8),
                        testMapBox(helper, 10, -56, 4, 17, -54, 8)), false))
                .withZone(new Zone(new ZoneId("gametest_test_map_room"), List.of(testMapBox(helper, 14, -60, 9, 16, -57, 18),
                        testMapBox(helper, 9, -60, 14, 16, -57, 18)), false))
                .withDoor(door);
        chase(helper, "test_map", map, testMap(1, -60, 0),
                List.of(testMap(16, -60, 4), testMap(16, -60, 6), testMap(15, -60, 5), testMap(16, -60, 7)), 220, clear);
    }
}
