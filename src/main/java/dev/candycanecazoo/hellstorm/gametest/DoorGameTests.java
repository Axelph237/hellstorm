package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.DoorFlights;
import dev.candycanecazoo.hellstorm.game.GameSounds;
import dev.candycanecazoo.hellstorm.game.Doors;
import dev.candycanecazoo.hellstorm.game.ForgeGameWorld;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.mob.path.FlowGraph;
import dev.candycanecazoo.hellstorm.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoorGameTests {
    private DoorGameTests() {}

    /**
     * Two rooms, x 0..3 (start) and x 5..8, split by a stone wall at x = 4 whose 1x2 doorway at z = 4 is bricked up: the
     * door. Buying it removes the bricks, unlocks the east room, and lets zombie pathing into it. Ending the game puts
     * the bricks back.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_buy")
    public static void buyingADoorOpensItUnlocksTheZoneBeyondAndTheResetBricksItUp(GameTestHelper helper) {
        for (int z = 0; z <= 8; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), z == 4 && dy < 2 ? Blocks.BRICKS : Blocks.STONE);
            }
        }
        BlockPos low = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        DoorDef door = DoorDef.empty("gate", 300).withMember(low).withMember(low.above());
        ZoneId west = new ZoneId("gametest_door_west");
        ZoneId east = new ZoneId("gametest_door_east");
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(west, List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)))), true))
                .withZone(new Zone(east, List.of(BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)))), false))
                .withDoor(door);
        helper.assertTrue(door.zones(ZoneIndex.of(map)).equals(Set.of(west, east)), "the door should border both rooms, got " + door.zones(ZoneIndex.of(map)));
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_door_test");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), passages, List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        Zombie template = EntityType.ZOMBIE.create(helper.getLevel());
        BlockPos eastCell = helper.absolutePos(new BlockPos(7, ROOM_Y, 4));
        try {
            helper.assertTrue(active.game().unlocked().equals(Set.of(west)), "the east room starts locked, got " + active.game().unlocked());
            helper.assertTrue(active.pathing().graph(template).indexOf(eastCell) < 0, "zombie pathing reaches a locked room");
            int start = PurchaseGameTests.points(active, player);

            helper.assertTrue(start >= 300, "the test needs 300 points to start with, has " + start);
            Doors.use(player, low.above());
            helper.assertTrue(helper.getLevel().getBlockState(low).isAir() && helper.getLevel().getBlockState(low.above()).isAir(), "the bricks are still there");
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 300, "expected to pay 300");
            helper.assertTrue(active.game().unlocked().equals(Set.of(west, east)), "the east room didn't unlock: " + active.game().unlocked());
            FlowGraph graph = active.pathing().graph(template);
            helper.assertTrue(graph.indexOf(eastCell) >= 0, "zombie pathing doesn't reach the unlocked room");
            helper.assertTrue(graph.fieldTo(eastCell).orElseThrow().distance(stand) < Integer.MAX_VALUE, "no walk from the start room through the doorway");
            Doors.use(player, low);
            helper.assertTrue(PurchaseGameTests.points(active, player) == start - 300, "an open door can't be bought again");
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(helper.getLevel().getBlockState(low).is(Blocks.BRICKS) && helper.getLevel().getBlockState(low.above()).is(Blocks.BRICKS),
                "ending the game should brick the doorway back up");
        helper.succeed();
    }

    /**
     * The same rooms, but drawn the way map makers often do: the west zone runs up to and over the wall, so the two zones'
     * boxes touch, with the door's barrels inside them. The east room must stay locked until the barrels go.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_touching")
    public static void aDoorDrawnInsideTouchingZonesStillKeepsTheFarOneLocked(GameTestHelper helper) {
        for (int z = -1; z <= 9; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), z == 4 && dy < 2 ? Blocks.BARREL : Blocks.STONE);
            }
        }
        BlockPos low = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        ZoneId west = new ZoneId("gametest_touching_west");
        ZoneId east = new ZoneId("gametest_touching_east");
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(west, List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(4, ROOM_Y + 2, 8)))), true))
                .withZone(new Zone(east, List.of(BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)))), false))
                .withDoor(DoorDef.empty("barrels", 300).withMember(low).withMember(low.above()));
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_door_touching_test");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), passages, List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        try {
            helper.assertTrue(active.game().unlocked().equals(Set.of(west)), "the east room starts locked, got " + active.game().unlocked());
            Doors.use(player, low);
            helper.assertTrue(active.game().unlocked().equals(Set.of(west, east)), "the east room didn't unlock: " + active.game().unlocked());
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /**
     * The rooms of the first test with a wooden door as the door players buy. Zombies walk through wooden doors, so the
     * world reads its cells as open, but until it's bought the east room is locked and its marker never spawns a zombie.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_wooden")
    public static void aWoodenDoorKeepsTheRoomBeyondLockedAndItsMarkerQuietUntilBought(GameTestHelper helper) {
        for (int z = 0; z <= 8; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), Blocks.STONE);
            }
        }
        BlockPos low = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(low, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.getLevel().setBlock(low.above(), oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        ZoneId west = new ZoneId("gametest_wooden_west");
        ZoneId east = new ZoneId("gametest_wooden_east");
        BlockPos westMarker = helper.absolutePos(new BlockPos(3, ROOM_Y, 8));
        BlockPos eastMarker = helper.absolutePos(new BlockPos(7, ROOM_Y, 4));
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(west, List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)))), true))
                .withZone(new Zone(east, List.of(BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)))), false))
                .withDoor(DoorDef.empty("oak", 300).withMember(low).withMember(low.above()))
                .withFeature(westMarker, FeatureKind.SPAWN)
                .withFeature(eastMarker, FeatureKind.SPAWN);
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_door_wooden_test");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), passages, List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        AABB rooms = new AABB(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(9, ROOM_Y + 3, 9)));
        try {
            List<Zombie> spawned = List.of();
            for (int tick = 0; tick < 2400 && spawned.size() < 6; tick++) {
                active.game().tick(active.world());
                spawned = helper.getLevel().getEntitiesOfClass(Zombie.class, rooms, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            }
            helper.assertTrue(spawned.size() >= 6, "only " + spawned.size() + " zombies spawned");
            for (Zombie zombie : spawned) {
                helper.assertTrue(zombie.blockPosition().distManhattan(westMarker) <= 2,
                        "a zombie spawned at " + zombie.blockPosition() + ", in the locked room (east marker " + eastMarker + ")");
            }
            helper.assertTrue(active.game().unlocked().equals(Set.of(west)), "the east room starts locked, got " + active.game().unlocked());
            Doors.use(player, low);
            helper.assertTrue(helper.getLevel().getBlockState(low).isAir(), "the wooden door wasn't bought");
            helper.assertTrue(active.game().unlocked().equals(Set.of(west, east)), "the east room didn't unlock: " + active.game().unlocked());
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "door_poor")
    public static void aDoorStaysShutForAPlayerWhoCantAffordIt(GameTestHelper helper) {
        BlockPos low = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        helper.setBlock(new BlockPos(4, ROOM_Y, 4), Blocks.BRICKS);
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_poor"), List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)))), true))
                .withDoor(DoorDef.empty("vault", 1_000_000).withMember(low));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_door_poor_test");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        try {
            int start = PurchaseGameTests.points(active, player);
            helper.assertTrue(Doors.use(player, low), "a door block should count as a door");
            helper.assertTrue(helper.getLevel().getBlockState(low).is(Blocks.BRICKS), "opened without the points");
            helper.assertTrue(PurchaseGameTests.points(active, player) == start, "charged for a refused door");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /**
     * The two rooms of the first test, split by the wall at x = 4. The wall's blocks where {@code isDoor(z, dy)} holds are
     * bricks, and they're the door.
     */
    private record Rooms(GameManager.Active active, ServerPlayer player, List<BlockPos> door) {
        static Rooms build(GameTestHelper helper, String name, BiPredicate<Integer, Integer> isDoor) {
            return build(helper, name, isDoor, DoorDef.Style.DOOR);
        }

        static Rooms build(GameTestHelper helper, String name, BiPredicate<Integer, Integer> isDoor, DoorDef.Style style) {
            List<BlockPos> members = new ArrayList<>();
            for (int z = 0; z <= 8; z++) {
                for (int dy = 0; dy <= 3; dy++) {
                    boolean door = isDoor.test(z, dy);
                    helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), door ? Blocks.BRICKS : Blocks.STONE);
                    if (door) {
                        members.add(helper.absolutePos(new BlockPos(4, ROOM_Y + dy, z)));
                    }
                }
            }
            DoorDef door = DoorDef.empty(name, 300).withStyle(style);
            for (BlockPos pos : members) {
                door = door.withMember(pos);
            }
            ZombiesMap map = ZombiesMap.EMPTY
                    .withZone(new Zone(new ZoneId("gametest_" + name + "_west"),
                            List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)))), true))
                    .withZone(new Zone(new ZoneId("gametest_" + name + "_east"),
                            List.of(BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)))), false))
                    .withDoor(door);
            Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
            ServerPlayer player = PurchaseGameTests.player(helper, "pap_" + name + "_test");
            BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
            player.moveTo(stand.getCenter());
            GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), passages, List.of(stand),
                    id -> id.equals(player.getUUID()) ? player : null);
            active.game().join(player.getUUID());
            PurchaseGameTests.play(active);
            return new Rooms(active, player, members);
        }

        boolean open() {
            return door.stream().allMatch(pos -> active.level().getBlockState(pos).isAir());
        }

        boolean bricked() {
            return door.stream().allMatch(pos -> active.level().getBlockState(pos).is(Blocks.BRICKS));
        }

        /** Runs a check, ending the game if it fails so the next batch can register its own. */
        void check(Runnable check) {
            try {
                check.run();
            } catch (RuntimeException | AssertionError e) {
                if (GameManager.get(active.level()).filter(a -> a == active).isPresent()) {
                    GameManager.end(active);
                }
                throw e;
            }
        }

        void later(GameTestHelper helper, long ticks, Runnable check) {
            helper.runAfterDelay(ticks, () -> check(check));
        }
    }

    private static List<Display.BlockDisplay> copies(GameTestHelper helper) {
        return helper.getEntities(EntityType.BLOCK_DISPLAY, new BlockPos(4, ROOM_Y, 4), 8).stream().filter(DoorFlights::isCopy).toList();
    }

    private static Block shown(Display.BlockDisplay copy) {
        CompoundTag tag = copy.saveWithoutId(new CompoundTag());
        return NbtUtils.readBlockState(copy.level().holderLookup(Registries.BLOCK), tag.getCompound("block_state")).getBlock();
    }

    /**
     * Buying a door clears the doorway at once and sends up a copy of each of its blocks, and the copies are all gone
     * once the flight is over.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_flight")
    public static void aBoughtDoorsBlocksBurstAsCopiesThatAreAllGoneAfterward(GameTestHelper helper) {
        Rooms rooms = Rooms.build(helper, "flight", (z, dy) -> z == 4 && dy < 2);
        rooms.check(() -> {
            Doors.use(rooms.player(), rooms.door().get(0));
            helper.assertTrue(rooms.open(), "the doorway should be clear the moment the door is bought");
            List<Display.BlockDisplay> copies = copies(helper);
            helper.assertTrue(copies.size() == 2, "expected a copy of each of the door's 2 blocks, got " + copies.size());
            helper.assertTrue(copies.stream().allMatch(c -> shown(c) == Blocks.BRICKS), "the copies should show the door's bricks");
        });
        rooms.later(helper, DoorFlights.MAX_TICKS + 1, () -> {
            helper.assertTrue(copies(helper).isEmpty(), "copies left after the flight: " + copies(helper).size());
            helper.assertTrue(rooms.open(), "the doorway closed again");
            GameManager.end(rooms.active());
            helper.succeed();
        });
    }

    private static float scale(Display.BlockDisplay copy) {
        return copy.saveWithoutId(new CompoundTag()).getCompound("transformation").getList("scale", net.minecraft.nbt.Tag.TAG_FLOAT).getFloat(0);
    }

    /**
     * Debris, like a door, rises shrunk a little, swells past full size on the impact sound's peak, shrinks to nothing,
     * and is gone once the flight is over.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_flight_debris")
    public static void debrisRisesThenBurstsOnTheImpactsPeak(GameTestHelper helper) {
        Rooms rooms = Rooms.build(helper, "debris", (z, dy) -> z == 4 && dy < 2, DoorDef.Style.DEBRIS);
        rooms.check(() -> {
            Doors.use(rooms.player(), rooms.door().get(0));
            helper.assertTrue(rooms.open() && copies(helper).size() == 2, "the debris should be cleared, leaving 2 copies");
        });
        rooms.later(helper, GameSounds.IMPACT_PEAK_TICKS - 2, () -> helper.assertTrue(copies(helper).stream().allMatch(c -> Math.abs(scale(c) - 0.85f) < 1e-4),
                "before the peak the copies should have risen at 0.85 size: " + copies(helper).stream().map(DoorGameTests::scale).toList()));
        rooms.later(helper, GameSounds.IMPACT_PEAK_TICKS + 1, () -> helper.assertTrue(copies(helper).stream().allMatch(c -> Math.abs(scale(c) - 1.25f) < 1e-4),
                "on the peak the copies should swell to 1.25: " + copies(helper).stream().map(DoorGameTests::scale).toList()));
        rooms.later(helper, GameSounds.IMPACT_PEAK_TICKS + 4, () -> helper.assertTrue(copies(helper).stream().allMatch(c -> scale(c) == 0),
                "then they should shrink to nothing: " + copies(helper).stream().map(DoorGameTests::scale).toList()));
        rooms.later(helper, DoorFlights.MAX_TICKS + 1, () -> {
            helper.assertTrue(copies(helper).isEmpty(), "copies left after the burst: " + copies(helper).size());
            GameManager.end(rooms.active());
            helper.succeed();
        });
    }

    /** Ending the game while the door is still in the air bricks the doorway back up, and no copy is left in the world. */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "door_flight_reset")
    public static void resettingMidFlightBricksTheDoorUpAndLeavesNoCopies(GameTestHelper helper) {
        Rooms rooms = Rooms.build(helper, "reset", (z, dy) -> z == 4 && dy < 2);
        rooms.check(() -> Doors.use(rooms.player(), rooms.door().get(0)));
        rooms.later(helper, DoorFlights.LAUNCH_TICKS + 3, () -> {
            helper.assertTrue(copies(helper).size() == 2, "the door should still be in the air, got " + copies(helper).size() + " copies");
            GameManager.end(rooms.active());
            helper.assertTrue(rooms.bricked(), "the reset should brick the doorway back up");
            helper.assertTrue(copies(helper).isEmpty(), "the reset left " + copies(helper).size() + " copies");
        });
        helper.runAfterDelay(DoorFlights.MAX_TICKS + 1, () -> {
            helper.assertTrue(rooms.bricked(), "the interrupted flight changed the bricked-up doorway");
            helper.assertTrue(copies(helper).isEmpty(), "copies came back after the reset");
            helper.succeed();
        });
    }

    /** A copy saved with its chunk mid-flight is refused when the chunk loads again. Fresh copies and other displays are left alone. */
    @GameTest(template = "floor", batch = "door_flight_disk")
    public static void aCopySavedMidFlightIsRefusedWhenItLoads(GameTestHelper helper) {
        Display.BlockDisplay copy = EntityType.BLOCK_DISPLAY.create(helper.getLevel());
        copy.addTag(DoorFlights.TAG);
        Display.BlockDisplay other = EntityType.BLOCK_DISPLAY.create(helper.getLevel());
        helper.assertTrue(MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(copy, helper.getLevel(), true)), "a copy loaded from disk was kept");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(copy, helper.getLevel(), false)), "a fresh copy was refused");
        helper.assertFalse(MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(other, helper.getLevel(), true)), "a map's own block display was refused");
        helper.succeed();
    }
}
