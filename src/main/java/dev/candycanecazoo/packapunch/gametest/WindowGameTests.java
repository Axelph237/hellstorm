package dev.candycanecazoo.packapunch.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.BarricadeBlock;
import dev.candycanecazoo.packapunch.game.ForgeGameWorld;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.GameMap;
import dev.candycanecazoo.packapunch.game.WindowOps;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import dev.candycanecazoo.packapunch.map.WindowDef;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
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

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindowGameTests {
    private WindowGameTests() {}

    private static WindowDef window(GameTestHelper helper, String id, int x0, int x1, int height) {
        return new WindowDef(id, BlockBox.of(helper.absolutePos(new BlockPos(x0, ROOM_Y, 4)), helper.absolutePos(new BlockPos(x1, ROOM_Y + height - 1, 4))),
                Direction.NORTH, 6, new ZoneId("gametest_window_room"));
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
                Direction.NORTH, 6, new ZoneId("gametest_breakin_room"));
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        BlockPos marker = helper.absolutePos(new BlockPos(4, ROOM_Y, 1));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(w.zone(), List.of(room), true)).withWindow(w).withFeature(marker, FeatureKind.SPAWN);
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
     * Two walled rooms, x 0..3 and x 5..8, joined through the wall at x = 4 by a 1x2 doorway, which {@code doorway} fills
     * (or leaves empty). {@code floor} covers the room floors (or leaves bare stone). The zombie spawns in the west room
     * and the player waits in the east room, so the zombie must come through the doorway.
     */
    private static void chaseThroughDoorway(GameTestHelper helper, java.util.function.Consumer<BlockPos> doorway, net.minecraft.world.level.block.Block floor,
                                            int doorwayHeight) {
        for (int z = 0; z <= 8; z++) {
            for (int dy = 0; dy <= 3; dy++) {
                if (!(z == 4 && dy < doorwayHeight)) {
                    helper.setBlock(new BlockPos(4, ROOM_Y + dy, z), Blocks.STONE);
                }
            }
            for (int x = 0; x <= 8; x++) {
                if (x != 4 && floor != Blocks.AIR) {
                    helper.setBlock(new BlockPos(x, ROOM_Y, z), floor);
                }
            }
        }
        doorway.accept(new BlockPos(4, ROOM_Y, 4));
        BlockBox west = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(3, ROOM_Y + 2, 8)));
        BlockBox east = BlockBox.of(helper.absolutePos(new BlockPos(5, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        BlockPos marker = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(new ZoneId("gametest_west"), List.of(west), false))
                .withZone(new Zone(new ZoneId("gametest_east"), List.of(east), true))
                .withFeature(marker, FeatureKind.SPAWN);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_chase_test"));
        BlockPos stand = helper.absolutePos(new BlockPos(8, ROOM_Y, 8));
        player.moveTo(stand.getCenter());
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2);
        helper.assertTrue(!passages.open().isEmpty(), "the doorway should connect the rooms");
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, passages.open(), 6.0), passages, List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        AABB area = west.toAabb().minmax(east.toAabb()).inflate(1);
        helper.succeedWhen(() -> {
            player.moveTo(stand.getCenter());
            List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet");
            helper.assertTrue(zombies.stream().anyMatch(z -> east.contains(z.blockPosition())),
                    "no zombie in the east room yet; positions " + zombies.stream().map(z -> z.blockPosition().toShortString()).toList());
            int followed = active.pathing().plansFollowed();
            GameManager.end(active);
            helper.assertTrue(followed > 0, "the zombie got there without following a cross-zone plan");
        });
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

    /** An iron golem (1.4 x 2.7, so 2x3) can't come through a 1x2 window; a zombie (1x2) can. */
    @GameTest(template = "floor")
    public static void theDirectorOnlySpawnsMobsThatFitTheMarkersWindow(GameTestHelper helper) {
        WindowDef w = window(helper, "fit", 4, 4, 2);
        BlockPos marker = w.attackSpot().relative(w.outward(), 2);
        BlockBox room = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 5)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8)));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(w.zone(), List.of(room), true)).withWindow(w).withFeature(marker, FeatureKind.SPAWN);
        GameMap gameMap = GameMap.of(map, Set.of(), 6.0);
        net.minecraft.resources.ResourceLocation golem = net.minecraft.resources.ResourceLocation.tryBuild("test", "golem");
        net.minecraft.resources.ResourceLocation zombie = net.minecraft.resources.ResourceLocation.tryBuild("test", "zombie");
        java.util.Map<net.minecraft.resources.ResourceLocation, dev.candycanecazoo.packapunch.mob.MobProfile> profiles = java.util.Map.of(
                golem, new dev.candycanecazoo.packapunch.mob.MobProfile(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_golem"), 1, 1,
                        java.util.Optional.empty(), dev.candycanecazoo.packapunch.mob.MobProfile.Ai.REPLACE, java.util.Optional.empty(), 1, 1, 4, true),
                zombie, dev.candycanecazoo.packapunch.mob.MobProfile.FALLBACK);
        ForgeGameWorld world = new ForgeGameWorld(helper.getLevel(), gameMap, List.of(), List::of, id -> null, profiles, id -> false, 30,
                new dev.candycanecazoo.packapunch.game.GamePathing(helper.getLevel(), gameMap,
                        Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(helper.getLevel()), 2)));
        helper.assertTrue(gameMap.windowFor(marker).isPresent(), "marker should link to the window");
        helper.assertTrue(!world.canSpawn(golem, marker), "iron golem allowed at a 1x2 window");
        helper.assertTrue(world.canSpawn(zombie, marker), "zombie refused at a 1x2 window");
        helper.succeed();
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
