package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.GamePhase;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.mob.AiInjector;
import dev.candycanecazoo.hellstorm.mob.MobProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.FLOOR_Y;
import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/**
 * Zombies know where every player is: a game mob goes for its target however far away and out of sight they are. These
 * arenas reach well past the 9x9 test floor, so each test has a batch to itself and clears its arena when it passes.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarTargetGameTests {
    private static final AiInjector.Stats STATS = new AiInjector.Stats(200.0, 0.35, 0f);

    private FarTargetGameTests() {}

    /**
     * Floors {@code width} x {@code length} cells from the test origin, builds {@code wall} cells up to a stone ceiling four
     * blocks up, and takes out any mobs left from an earlier run. The ceiling seals the arena from the world around it: the
     * test world is kept between runs and may be natural terrain, where lava flowed in over open-topped walls. Returns what
     * clears it again, mobs and all.
     */
    private static Runnable build(GameTestHelper helper, int width, int length, BiPredicate<Integer, Integer> wall) {
        AABB arena = new AABB(helper.absolutePos(new BlockPos(0, FLOOR_Y, 0)), helper.absolutePos(new BlockPos(width, ROOM_Y + 4, length)));
        Runnable discardMobs = () -> helper.getLevel().getEntitiesOfClass(Mob.class, arena).forEach(Entity::discard);
        discardMobs.run();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
                Block block = wall.test(x, z) ? Blocks.STONE : Blocks.AIR;
                for (int dy = 0; dy <= 3; dy++) {
                    helper.setBlock(new BlockPos(x, ROOM_Y + dy, z), block);
                }
                helper.setBlock(new BlockPos(x, ROOM_Y + 4, z), Blocks.STONE);
            }
        }
        return () -> {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < length; z++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        helper.setBlock(new BlockPos(x, ROOM_Y + dy, z), Blocks.AIR);
                    }
                }
            }
            discardMobs.run();
        };
    }

    /**
     * A cup 30 wide and 29 deep, open to the north, with the mob near its closed south end and the player 19 blocks
     * south of it, behind the wall. The only way is back out of the cup and round: about 110 blocks. Vanilla pathfinding
     * searches the whole cup (870 cells) before it finds the way out, more than a zombie's or a wolf's own node budget
     * allows, so this needs the node budget of the game's follow range.
     */
    private static void walkOutOfADeadEnd(GameTestHelper helper, EntityType<? extends Mob> type) {
        Runnable clear = build(helper, 37, 53, (x, z) -> x == 0 || x == 36 || z == 0 || z == 52
                || ((x == 2 || x == 33) && z >= 3 && z <= 32) || (z == 32 && x >= 2 && x <= 33));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_far_dead_end");
        player.moveTo(helper.absolutePos(new BlockPos(17, ROOM_Y, 49)).getCenter());
        Mob mob = helper.spawn(type, new BlockPos(17, ROOM_Y, 30));
        MobProfile profile = new MobProfile(EntityType.getKey(type), 10, 1, Optional.empty(), MobProfile.Ai.REPLACE, Optional.empty(),
                1, 1, 0, false, MobProfile.Navigation.VANILLA);
        AiInjector.inject(mob, profile, STATS, () -> List.of(player));
        helper.succeedWhen(() -> {
            player.moveTo(helper.absolutePos(new BlockPos(17, ROOM_Y, 49)).getCenter());
            helper.assertTrue(mob.getTarget() == player, "target is " + mob.getTarget());
            helper.assertTrue(mob.distanceTo(player) < 2.5, "the " + EntityType.getKey(type) + " is at " + helper.relativeVec(mob.position())
                    + ", " + String.format("%.1f", mob.distanceTo(player)) + " blocks from the player");
            clear.run();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 1600, batch = "far_dead_end_zombie")
    public static void aVanillaNavigatingZombieWalksOutOfADeadEndToAFarPlayer(GameTestHelper helper) {
        walkOutOfADeadEnd(helper, EntityType.ZOMBIE);
    }

    @GameTest(template = "floor", timeoutTicks = 1600, batch = "far_dead_end_hound")
    public static void aVanillaNavigatingWolfWalksOutOfADeadEndToAFarPlayer(GameTestHelper helper) {
        walkOutOfADeadEnd(helper, EntityType.WOLF);
    }

    /**
     * A game's hall 9 wide and 49 long, cut by a wall with a gap at its east end. The mob spawns at the far end, 46 blocks
     * from the player and out of sight, and must target the player at once and reach them steering on the flow field.
     */
    private static void chaseTheLengthOfAHall(GameTestHelper helper, String profile, Class<? extends Mob> kind) {
        Runnable clear = build(helper, 9, 49, (x, z) -> z == 24 && x < 8);
        BlockBox hall = BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 48)));
        BlockPos marker = helper.absolutePos(new BlockPos(1, ROOM_Y, 47));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_far_hall"), List.of(hall), true)).withFeature(marker, FeatureKind.SPAWN);
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_far_hall");
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of(), 6.0), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        while (active.game().phase() instanceof GamePhase.Countdown) {
            active.game().tick(active.world());
        }
        player.setInvulnerable(true);
        UUID id = active.world().spawnZombie(marker, new GameWorld.ZombieSpec(ResourceLocation.tryBuild(Hellstorm.MOD_ID, profile), 200, 0.3))
                .orElseThrow(() -> new IllegalStateException(profile + " didn't spawn"));
        Mob mob = (Mob) helper.getLevel().getEntity(id);
        helper.assertTrue(kind.isInstance(mob), profile + " spawned " + mob);
        helper.assertTrue(mob.distanceTo(player) > 40, "the " + profile + " should start far away, not " + mob.distanceTo(player) + " blocks");
        List<Integer> targetedAt = new ArrayList<>();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (targetedAt.isEmpty() && mob.getTarget() == player) {
                targetedAt.add(ticks[0]);
            }
        });
        helper.succeedWhen(() -> {
            player.moveTo(stand.getCenter());
            helper.assertTrue(mob.distanceTo(player) < 2.5, "the " + profile + " is " + String.format("%.1f", mob.distanceTo(player))
                    + " blocks from the player; flow steps " + active.pathing().flowSteps());
            int steps = active.pathing().flowSteps();
            GameManager.end(active);
            clear.run();
            helper.assertTrue(!targetedAt.isEmpty() && targetedAt.get(0) <= 5, "the " + profile + " took " + targetedAt + " ticks to target the player");
            helper.assertTrue(steps > 0, "the " + profile + " got there without steering on a flow field");
        });
    }

    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_far_zombie")
    public static void aGameZombieChasesAFarPlayerItCannotSee(GameTestHelper helper) {
        chaseTheLengthOfAHall(helper, "zombie", net.minecraft.world.entity.monster.Zombie.class);
    }

    @GameTest(template = "floor", timeoutTicks = 1200, batch = "game_far_hound")
    public static void aGameHoundChasesAFarPlayerItCannotSee(GameTestHelper helper) {
        chaseTheLengthOfAHall(helper, "hellhound", net.minecraft.world.entity.animal.Wolf.class);
    }

    /** When its target stops being one (downed, gone), a zombie turns to the next nearest player within half a second. */
    @GameTest(template = "floor")
    public static void aZombieTurnsToTheFarPlayerWhenTheNearOneStopsBeingATarget(GameTestHelper helper) {
        ServerPlayer near = PurchaseGameTests.player(helper, "pap_retarget_near");
        ServerPlayer far = PurchaseGameTests.player(helper, "pap_retarget_far");
        near.moveTo(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)).getCenter());
        far.moveTo(helper.absolutePos(new BlockPos(7, ROOM_Y, 7)).getCenter());
        List<ServerPlayer> targets = new ArrayList<>(List.of(near, far));
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, ROOM_Y, 2));
        AiInjector.inject(zombie, MobProfile.FALLBACK, STATS, () -> List.copyOf(targets));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(zombie.getTarget() == near, "the zombie should go for the nearer player first, not " + zombie.getTarget());
            targets.remove(near);
        });
        helper.runAfterDelay(31, () -> {
            helper.assertTrue(zombie.getTarget() == far, "11 ticks after the near player stopped being a target, the zombie targets " + zombie.getTarget());
            helper.succeed();
        });
    }
}
