package dev.candycanecazoo.hellstorm.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.game.ForgeGameWorld;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.tools.LevelCellProbe;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindowGameTests {
    private WindowGameTests() {}

    private static WindowDef window(GameTestHelper helper, String id, int x0, int x1, int height) {
        return new WindowDef(id, BlockBox.of(helper.absolutePos(new BlockPos(x0, ROOM_Y, 4)), helper.absolutePos(new BlockPos(x1, ROOM_Y + height - 1, 4))),
                Direction.NORTH, 6);
    }

    /** All members show the same count after each change, for a 1x1, a 3x2, and a 5x4 (default max) window. */
    @GameTest(template = "floor")
    public static void everyBlockOfAWindowSharesItsBoards(GameTestHelper helper) {
        for (WindowDef w : List.of(window(helper, "one", 1, 1, 1), window(helper, "three", 3, 5, 2), window(helper, "max", 2, 6, 4))) {
            for (int boards : new int[] {6, 4, 0, 1}) {
                WindowOps.setBoards(helper.getLevel(), w, boards);
                for (BlockPos pos : BlockPos.betweenClosed(w.opening().min(), w.opening().max())) {
                    BlockState state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(state.is(PapRegistries.BARRICADE.get()) && state.getValue(BarricadeBlock.BOARDS) == boards,
                            w.id() + " at " + pos + " shows " + state + ", expected " + boards + " boards");
                }
                helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == boards, w.id() + " reads back wrong");
            }
            WindowOps.tearBoard(helper.getLevel(), w);
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 0, "tearing at 0 stays 0");
        }
        helper.succeed();
    }

    /** Game mobs pass only with no boards; players never pass; projectiles always pass. */
    @GameTest(template = "floor")
    public static void barricadeCollisionDependsOnWhoIsMoving(GameTestHelper helper) {
        WindowDef w = window(helper, "collide", 3, 3, 2);
        BlockPos cell = w.opening().min();
        Zombie gameZombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, ROOM_Y, 1));
        gameZombie.addTag(ForgeGameWorld.MOB_TAG);
        Zombie wildZombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, ROOM_Y, 1));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_window_test"));
        Arrow arrow = new Arrow(helper.getLevel(), 0, 0, 0);
        for (int boards : new int[] {3, 0}) {
            WindowOps.setBoards(helper.getLevel(), w, boards);
            BlockState state = helper.getLevel().getBlockState(cell);
            boolean zombieBlocked = !state.getCollisionShape(helper.getLevel(), cell, CollisionContext.of(gameZombie)).isEmpty();
            helper.assertTrue(zombieBlocked == (boards > 0), "game zombie blocked=" + zombieBlocked + " at " + boards + " boards");
            helper.assertTrue(!state.getCollisionShape(helper.getLevel(), cell, CollisionContext.of(wildZombie)).isEmpty(), "other mobs never pass");
            helper.assertTrue(!state.getCollisionShape(helper.getLevel(), cell, CollisionContext.of(player)).isEmpty(), "players never pass");
            helper.assertTrue(state.getCollisionShape(helper.getLevel(), cell, CollisionContext.of(arrow)).isEmpty(), "projectiles always pass");
            var playerShape = state.getCollisionShape(helper.getLevel(), cell, CollisionContext.of(player)).bounds();
            helper.assertTrue(playerShape.minZ >= 0.8 && playerShape.maxZ == 1.0 && playerShape.maxY == 1.0,
                    "players should hit a thin panel on the inside (south) face of a north-facing window, got " + playerShape);
            boolean openWithoutContext = state.getCollisionShape(helper.getLevel(), cell, CollisionContext.empty()).isEmpty();
            helper.assertTrue(openWithoutContext == (boards == 0), "context-free collision (vanilla move control) open=" + openWithoutContext + " at " + boards + " boards");
        }
        helper.succeed();
    }

    /**
     * A walled room with a window in its north wall and a spawn marker outside. The game's zombie must tear every board
     * and end up inside the room.
     */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_window")
    public static void aZombieBreaksInThroughAWindow(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int dy = 0; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, ROOM_Y + dy, 4), Blocks.STONE);
            }
        }
        WindowDef w = new WindowDef("breakin", BlockBox.of(helper.absolutePos(new BlockPos(4, ROOM_Y, 4)), helper.absolutePos(new BlockPos(4, ROOM_Y + 1, 4))),
                Direction.NORTH, 6);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        BlockPos marker = helper.absolutePos(new BlockPos(4, ROOM_Y, 1));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_window_room"), List.of(room), true)).withWindow(w).withFeature(marker, FeatureKind.SPAWN);
        WindowOps.setBoards(helper.getLevel(), w, 6);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_breakin_test"));
        BlockPos stand = helper.absolutePos(new BlockPos(7, ROOM_Y, 7));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        AABB area = room.toAabb().minmax(new AABB(marker)).inflate(3);
        helper.succeedWhen(() -> {
            List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet");
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 0, "boards left: " + WindowOps.boards(helper.getLevel(), w));
            helper.assertTrue(zombies.stream().anyMatch(z -> room.contains(z.blockPosition())),
                    "no zombie inside yet; positions " + zombies.stream().map(z -> z.blockPosition().toShortString()).toList());
            GameManager.end(active);
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 6, "ending the game should board the window back up");
        });
    }

    /**
     * The repair key's packet, sent every 2 ticks while held, puts one board back each {@code windows.repair_ticks}, with
     * the bar on the repairer's HUD filling toward it; the board pays, and the repairer alone hears the cha-ching.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "game_repair_bar")
    public static void holdingTheRepairKeyRebuildsABoardEachTimeTheBarFills(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int dy = 0; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, ROOM_Y + dy, 4), Blocks.STONE);
            }
        }
        WindowDef w = window(helper, "repair_bar", 4, 4, 2);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_window_room"), List.of(room), true)).withWindow(w);
        WindowOps.setBoards(helper.getLevel(), w, 0);
        ListeningPlayer player = new ListeningPlayer(helper.getLevel(), "pap_repair_bar_test");
        ListeningPlayer teammate = new ListeningPlayer(helper.getLevel(), "pap_repair_bar_mate");
        BlockPos stand = helper.absolutePos(new BlockPos(4, ROOM_Y, 6));
        player.moveTo(stand.getCenter());
        teammate.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : id.equals(teammate.getUUID()) ? teammate : null);
        active.game().join(player.getUUID());
        active.game().join(teammate.getUUID());
        PurchaseGameTests.play(active);
        // The game's own start music isn't what this is about.
        player.heard.clear();
        teammate.heard.clear();
        int perBoard = active.game().settings().windows().repairTicks();
        int startPoints = PurchaseGameTests.points(active, player);
        BlockPos barricade = w.opening().min();
        helper.onEachTick(() -> {
            if (helper.getTick() % 2 == 0 && helper.getTick() < 2L * perBoard + 6) {
                dev.candycanecazoo.hellstorm.net.UsePacket.apply(player, barricade, false);
            }
        });
        helper.runAfterDelay(perBoard / 2, () -> step(active, () -> {
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 0, "a board went on before the bar filled");
            float progress = active.game().status(player.getUUID()).bar().map(b -> b.label().equals("Rebuilding") ? b.progress() : -1f).orElse(-1f);
            helper.assertTrue(progress > 0.2f && progress < 0.8f, "halfway through a board the bar should be about half full, is " + progress);
        }));
        helper.runAfterDelay(perBoard + 4, () -> step(active, () -> {
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 1, "one board should be on after one board's time, got "
                    + WindowOps.boards(helper.getLevel(), w));
            helper.assertTrue(PurchaseGameTests.points(active, player) > startPoints, "the board didn't pay");
            helper.assertTrue(player.heard.equals(List.of(PapRegistries.CHA_CHING.get())), "the repairer should hear one cha-ching, heard " + player.heard);
            helper.assertTrue(teammate.heard.isEmpty(), "a teammate heard " + teammate.heard);
        }));
        helper.runAfterDelay(2 * perBoard + 5, () -> step(active, () -> {
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 2, "two boards after two boards' time, got " + WindowOps.boards(helper.getLevel(), w));
        }));
        helper.runAfterDelay(3 * perBoard + 8, () -> step(active, () -> {
            helper.assertTrue(WindowOps.boards(helper.getLevel(), w) == 2, "a board went on after letting go");
            helper.assertTrue(active.game().status(player.getUUID()).bar().isEmpty(), "the bar stayed after letting go");
            GameManager.end(active);
            helper.succeed();
        }));
    }

    private static void step(GameManager.Active active, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | AssertionError e) {
            GameManager.end(active);
            throw e;
        }
    }

    /** The repair key's packet holds a window in reach, and ignores one out of reach. */
    @GameTest(template = "floor", batch = "game_repair_key")
    public static void theRepairKeyRepairsOnlyWithinReach(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int dy = 0; dy <= 2; dy++) {
                helper.setBlock(new BlockPos(x, ROOM_Y + dy, 4), Blocks.STONE);
            }
        }
        WindowDef w = window(helper, "repair_key", 4, 4, 2);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_window_room"), List.of(room), true)).withWindow(w);
        WindowOps.setBoards(helper.getLevel(), w, 0);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_repair_key_test"));
        BlockPos stand = helper.absolutePos(new BlockPos(4, ROOM_Y, 6));
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        BlockPos barricade = w.opening().min();
        try {
            player.moveTo(helper.absolutePos(new BlockPos(4, ROOM_Y, 8)).offset(0, 0, 20).getCenter());
            helper.assertFalse(dev.candycanecazoo.hellstorm.net.UsePacket.apply(player, barricade, false), "repaired from 20 blocks away");
            player.moveTo(stand.getCenter());
            helper.assertTrue(dev.candycanecazoo.hellstorm.net.UsePacket.apply(player, barricade, false), "no repair from beside the window");
            helper.assertTrue(active.game().status(player.getUUID()).bar().isPresent(), "repairing from beside the window shows no bar");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /** With the server's settings, a window stops paying at {@code economy.repair_cap_per_window} and a different window still pays. */
    @GameTest(template = "floor", batch = "game_repair_window_cap")
    public static void aWindowStopsPayingAtItsCapButAnotherStillPays(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_repair_cap_test"));
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        try {
            var economy = active.game().settings().economy();
            helper.assertTrue(economy.repair() > 0 && economy.repairCapPerWindow() + economy.repair() <= economy.repairCapPerRound(),
                    "the server's settings leave no room to see the window cap: " + economy);
            int paidAtFirst = 0;
            for (int board = 0; board < economy.repairCapPerWindow() / economy.repair() + 3; board++) {
                paidAtFirst += active.game().onRepair(player.getUUID(), "first");
            }
            helper.assertTrue(paidAtFirst == economy.repairCapPerWindow(), "one window paid " + paidAtFirst + ", its cap is " + economy.repairCapPerWindow());
            int paidAtSecond = active.game().onRepair(player.getUUID(), "second");
            helper.assertTrue(paidAtSecond == economy.repair(), "a different window paid " + paidAtSecond + ", expected " + economy.repair());
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /**
     * A window whose bottom row sits one block up on {@code sill}, reached from a 1-wide, 3-high corridor outside, like a
     * window set in a wall above a step. The zombie has to jump onto the sill to climb in.
     */
    private static void breakInOverSill(GameTestHelper helper, BlockState sill) {
        for (int x = 0; x <= 8; x++) {
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(x, ROOM_Y + dy, 4), Blocks.STONE);
            }
        }
        for (int z = 0; z <= 3; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(3, ROOM_Y + dy, z), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(5, ROOM_Y + dy, z), Blocks.STONE_BRICKS);
            }
            helper.setBlock(new BlockPos(4, ROOM_Y + 3, z), Blocks.STONE_BRICKS);
        }
        helper.setBlock(new BlockPos(4, ROOM_Y, 4), sill);
        WindowDef w = new WindowDef("sill", BlockBox.of(helper.absolutePos(new BlockPos(4, ROOM_Y + 1, 4)), helper.absolutePos(new BlockPos(4, ROOM_Y + 2, 4))),
                Direction.NORTH, 6);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        BlockPos marker = helper.absolutePos(new BlockPos(4, ROOM_Y, 1));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_window_room"), List.of(room), true)).withWindow(w).withFeature(marker, FeatureKind.SPAWN);
        WindowOps.setBoards(helper.getLevel(), w, 1);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_sill_test"));
        BlockPos stand = helper.absolutePos(new BlockPos(7, ROOM_Y, 7));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        AABB area = room.toAabb().minmax(new AABB(marker)).inflate(3);
        helper.succeedWhen(() -> {
            player.moveTo(stand.getCenter());
            List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet");
            helper.assertTrue(zombies.stream().anyMatch(z -> room.contains(z.blockPosition())),
                    "no zombie inside yet; boards " + WindowOps.boards(helper.getLevel(), w) + ", positions "
                            + zombies.stream().map(z -> z.position().toString()).toList());
            GameManager.end(active);
        });
    }

    @GameTest(template = "floor", timeoutTicks = 600, batch = "game_sill_block")
    public static void aZombieJumpsOntoABlockSillToClimbIn(GameTestHelper helper) {
        breakInOverSill(helper, Blocks.STONE.defaultBlockState());
    }

    /** A stair sill turned sideways: half of it is a full block high, the other half a step. */
    @GameTest(template = "floor", timeoutTicks = 600, batch = "game_sill_stair")
    public static void aZombieClimbsInOverASidewaysStairSill(GameTestHelper helper) {
        breakInOverSill(helper, Blocks.ANDESITE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST));
    }

    /**
     * Two walled rooms, x 0..3 and x 5..8, joined through the wall at x = 4 by a 1x2 doorway, which {@code doorway} fills
     * (or leaves empty). {@code floor} covers the room floors (or leaves bare stone). The zombie spawns in the west room
     * and the player waits in the east room, so the zombie must come through the doorway. Both are start rooms, so the
     * west room's marker spawns without the player going in.
     */
    private static void chaseThroughDoorway(GameTestHelper helper, java.util.function.Consumer<BlockPos> doorway, net.minecraft.world.level.block.Block floor,
                                            int doorwayHeight) {
        chase(helper, 4, doorwayHeight, doorway, floor, () -> {}, new BlockPos(0, ROOM_Y, 0), new BlockPos(8, ROOM_Y, 8));
    }

    /**
     * The two rooms of {@link #chaseThroughDoorway} with the doorway at {@code doorZ}, {@code decorate} run after the
     * walls are built, and the marker and player where given. Passes once a zombie reaches the east room without ever
     * stalling.
     */
    private static void chase(GameTestHelper helper, int doorZ, int doorwayHeight, java.util.function.Consumer<BlockPos> doorway,
                              net.minecraft.world.level.block.Block floor, Runnable decorate, BlockPos markerAt, BlockPos standAt) {
        for (int z = 0; z <= 8; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                if (!(z == doorZ && dy < doorwayHeight)) {
                    helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), Blocks.STONE);
                }
            }
            for (int x = 0; x <= 8; x++) {
                if (x != 4 && floor != Blocks.AIR) {
                    helper.setBlock(new BlockPos(x, ROOM_Y, z), floor);
                }
            }
        }
        doorway.accept(new BlockPos(4, ROOM_Y, doorZ));
        decorate.run();
        BlockBox west = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)));
        BlockBox east = BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        BlockPos marker = helper.absolutePos(markerAt);
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_west"), List.of(west), true))
                .withZone(new Zone(new ZoneId("gametest_east"), List.of(east), true))
                .withFeature(marker, FeatureKind.SPAWN);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_chase_test"));
        BlockPos stand = helper.absolutePos(standAt);
        player.moveTo(stand.getCenter());
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        helper.assertTrue(!passages.open().isEmpty(), "the doorway should connect the rooms");
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        AABB area = west.toAabb().minmax(east.toAabb()).inflate(1);
        helper.succeedWhen(() -> {
            player.moveTo(stand.getCenter());
            List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet");
            helper.assertTrue(zombies.stream().anyMatch(z -> east.contains(z.blockPosition())),
                    "no zombie in the east room yet; stalls " + active.pathing().stalls() + ", positions relative to the test "
                            + zombies.stream().map(z -> helper.relativeVec(z.position())).map(v -> String.format("(%.2f %.2f %.2f)", v.x, v.y, v.z)).toList());
            int stalls = active.pathing().stalls();
            GameManager.end(active);
            helper.assertTrue(stalls == 0, "zombies stalled " + stalls + " times on the way");
        });
    }

    private static BlockState openTrapdoor(Direction facing) {
        return Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, facing);
    }

    /**
     * The wall between the rooms is open trapdoors at z 0..3, their panels across the way, so it only looks open; the real
     * doorway is at z = 8, with a trapdoor panel lining its south side. The straight line to the player crosses the decoy.
     */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_trapdoor_decoy")
    public static void aZombieGoesRoundATrapdoorWallToTheRealDoorway(GameTestHelper helper) {
        chase(helper, 8, 2, pos -> helper.setBlock(pos, openTrapdoor(Direction.NORTH)), Blocks.AIR, () -> {
            for (int z = 0; z <= 3; z++) {
                for (int dy = 0; dy <= 3; dy++) {
                    helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), openTrapdoor(Direction.WEST));
                }
            }
        }, new BlockPos(0, ROOM_Y, 1), new BlockPos(8, ROOM_Y, 1));
    }

    /** A doorway at the near end of the wall, so the zombie has to turn tightly round its jamb. */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_tight_corner")
    public static void aZombieTurnsTightlyRoundADoorJamb(GameTestHelper helper) {
        chase(helper, 1, 2, pos -> {}, Blocks.AIR, () -> {}, new BlockPos(0, ROOM_Y, 7), new BlockPos(8, ROOM_Y, 0));
    }

    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_cross_zone")
    public static void aZombieChasesThePlayerIntoAnotherZone(GameTestHelper helper) {
        chaseThroughDoorway(helper, pos -> {}, Blocks.AIR, 2);
    }

    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_open_door")
    public static void aZombieChasesThroughAnOpenDoor(GameTestHelper helper) {
        chaseThroughDoorway(helper, pos -> {
            helper.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
                    .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST));
            helper.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
                    .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }, Blocks.AIR, 2);
    }

    /** Game zombies open wooden doors, so a closed one doesn't stop them. */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_closed_door")
    public static void aZombieOpensAClosedWoodenDoor(GameTestHelper helper) {
        chaseThroughDoorway(helper, pos -> {
            helper.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST));
            helper.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }, Blocks.AIR, 2);
    }

    /**
     * A wall of glass panes, iron bars, and fences splits one room, with a gap at the far end. Vanilla's walk evaluator
     * sees all three as walls, so the flow graph leaves them out and the walking cost goes round through the gap.
     */
    @GameTest(template = "floor")
    public static void theFlowGraphRoutesRoundPanesBarsAndFences(GameTestHelper helper) {
        for (int z = 0; z <= 7; z++) {
            net.minecraft.world.level.block.Block wall = z < 4 ? Blocks.GLASS_PANE : z < 6 ? Blocks.IRON_BARS : Blocks.OAK_FENCE;
            for (int dy = 0; dy <= 3; dy++) {
                helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), wall);
            }
        }
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_split"), List.of(room), true));
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        Zombie template = EntityType.ZOMBIE.create(helper.getLevel());
        try (dev.candycanecazoo.hellstorm.game.GamePathing pathing =
                     new dev.candycanecazoo.hellstorm.game.GamePathing(helper.getLevel(), GameMap.of(map, passages.open(), 6.0))) {
            dev.candycanecazoo.hellstorm.mob.path.FlowGraph graph = pathing.graph(template);
            for (int z = 0; z <= 7; z++) {
                helper.assertTrue(graph.indexOf(helper.absolutePos(new BlockPos(4, ROOM_Y, z))) < 0, "wall cell z=" + z + " is walkable");
            }
            helper.assertTrue(graph.indexOf(helper.absolutePos(new BlockPos(4, ROOM_Y, 8))) >= 0, "the gap isn't walkable");
            int cost = graph.fieldTo(helper.absolutePos(new BlockPos(8, ROOM_Y, 0))).orElseThrow()
                    .distance(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)));
            // Straight across is 8 blocks (cost 80); round through the gap is about 19.
            helper.assertTrue(cost > 150 && cost < 300, "walking cost " + cost + " doesn't go round through the gap");
        }
        helper.succeed();
    }

    /** An iron golem (1.4 x 2.7, so 2x3) can't come through a 1x2 window; a zombie (1x2) can, but not one scaled up past 2 tall. */
    @GameTest(template = "floor")
    public static void theDirectorOnlySpawnsMobsThatFitTheMarkersWindow(GameTestHelper helper) {
        WindowDef w = window(helper, "fit", 4, 4, 2);
        BlockPos marker = w.attackSpot().relative(w.outward(), 2);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_window_room"), List.of(room), true)).withWindow(w).withFeature(marker, FeatureKind.SPAWN);
        GameMap gameMap = GameMap.of(map, Set.of(), 6.0);
        net.minecraft.resources.ResourceLocation golem = net.minecraft.resources.ResourceLocation.tryBuild("test", "golem");
        net.minecraft.resources.ResourceLocation zombie = net.minecraft.resources.ResourceLocation.tryBuild("test", "zombie");
        net.minecraft.resources.ResourceLocation big = net.minecraft.resources.ResourceLocation.tryBuild("test", "big_zombie");
        java.util.Map<net.minecraft.resources.ResourceLocation, dev.candycanecazoo.hellstorm.mob.MobProfile> profiles = java.util.Map.of(
                golem, new dev.candycanecazoo.hellstorm.mob.MobProfile(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_golem"), 1, 1,
                        java.util.Optional.empty(), dev.candycanecazoo.hellstorm.mob.MobProfile.Ai.REPLACE, java.util.Optional.empty(), 1, 1, 4, true, dev.candycanecazoo.hellstorm.mob.MobProfile.Navigation.FLOW, 1.0),
                zombie, dev.candycanecazoo.hellstorm.mob.MobProfile.FALLBACK,
                big, scaled(dev.candycanecazoo.hellstorm.mob.MobProfile.FALLBACK, 1.5));
        ForgeGameWorld world = new ForgeGameWorld(helper.getLevel(), gameMap, List.of(), List::of, id -> null, profiles, id -> false, dev.candycanecazoo.hellstorm.config.GameSettings.DEFAULT,
                new dev.candycanecazoo.hellstorm.game.GamePathing(helper.getLevel(), gameMap));
        helper.assertTrue(gameMap.windowFor(marker).isPresent(), "marker should link to the window");
        helper.assertTrue(!world.canSpawn(golem, marker), "iron golem allowed at a 1x2 window");
        helper.assertTrue(world.canSpawn(zombie, marker), "zombie refused at a 1x2 window");
        helper.assertTrue(!world.canSpawn(big, marker), "a zombie at 1.5 times its size (0.9 x 2.9, so 1x3) allowed at a 1x2 window");
        helper.succeed();
    }

    private static dev.candycanecazoo.hellstorm.mob.MobProfile scaled(dev.candycanecazoo.hellstorm.mob.MobProfile p, double scale) {
        return new dev.candycanecazoo.hellstorm.mob.MobProfile(p.entity(), p.weight(), p.minRound(), p.maxRound(), p.ai(), p.nbt(), p.healthMultiplier(),
                p.speedMultiplier(), p.damage(), p.breaksBarricades(), p.navigation(), scale);
    }

    /** Carpet raises the floor 1/16 block, so a 1.95-tall zombie needs a 3-tall doorway over it (a 2-tall one is too low in vanilla too). */
    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_carpet")
    public static void aZombieChasesAcrossCarpet(GameTestHelper helper) {
        chaseThroughDoorway(helper, pos -> helper.setBlock(pos, Blocks.RED_CARPET), Blocks.RED_CARPET, 3);
    }

    /** Vanilla's in-wall check ignores per-entity collision, so a mob climbing through a bare window must not suffocate. */
    @GameTest(template = "floor")
    public static void climbingThroughABareWindowDoesNotSuffocate(GameTestHelper helper) {
        WindowDef w = window(helper, "suffocate", 3, 3, 2);
        WindowOps.setBoards(helper.getLevel(), w, 0);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, ROOM_Y, 4));
        zombie.addTag(ForgeGameWorld.MOB_TAG);
        zombie.moveTo(w.opening().min().getCenter().add(0, -0.5, 0));
        helper.assertTrue(!zombie.isInWall(), "a game zombie inside a bare window counts as stuck in a wall");
        helper.succeed();
    }
}
