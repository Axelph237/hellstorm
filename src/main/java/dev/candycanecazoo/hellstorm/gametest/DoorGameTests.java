package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.Doors;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.mob.path.FlowGraph;
import dev.candycanecazoo.hellstorm.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

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
}
