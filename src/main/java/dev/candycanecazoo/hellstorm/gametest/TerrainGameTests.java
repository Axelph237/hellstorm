package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.ExplodeUtil;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/**
 * Nothing changes the terrain while a match is underway, guns included; outside one, explosions and bullets work as they
 * always do. Each test with a game has a batch of its own, since a game registered in the same batch would cover the
 * others' level too.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerrainGameTests {
    private static final BlockPos CENTER = new BlockPos(4, ROOM_Y + 2, 4);

    private TerrainGameTests() {}

    /** Glass on all six sides of {@link #CENTER}: weak enough that any blast there breaks it. */
    private static List<BlockPos> glassShell(GameTestHelper helper) {
        List<BlockPos> shell = Direction.stream().map(d -> helper.absolutePos(CENTER.relative(d))).toList();
        shell.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()));
        return shell;
    }

    private static long glassLeft(GameTestHelper helper, List<BlockPos> shell) {
        return shell.stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.GLASS)).count();
    }

    private static Vec3 center(GameTestHelper helper) {
        return Vec3.atCenterOf(helper.absolutePos(CENTER));
    }

    /** A game over the test's floor, started and past its countdown when {@code started}, else left in its lobby. */
    private static GameManager.Active game(GameTestHelper helper, boolean started) {
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_terrain"),
                List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8)))), true));
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_terrain");
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()),
                List.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1))), id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        if (started) {
            PurchaseGameTests.play(active);
            helper.assertTrue(GameManager.matchUnderway(helper.getLevel()), "the match should be underway, phase " + active.game().phase());
        }
        return active;
    }

    /** TacZ's own explosion call with block damage asked for, as a rocket makes on impact. Returns the glass left of 6. */
    private static long gunBlast(GameTestHelper helper) {
        List<BlockPos> shell = glassShell(helper);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, ROOM_Y, 1));
        ExplodeUtil.createExplosion(shooter, shooter, 10f, 3f, false, true, center(helper));
        return glassLeft(helper, shell);
    }

    private static long vanillaBlast(GameTestHelper helper) {
        List<BlockPos> shell = glassShell(helper);
        Vec3 at = center(helper);
        helper.getLevel().explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
        return glassLeft(helper, shell);
    }

    /** What an incendiary bullet left: whether the glass it hit is whole, and whether the floor it hit is free of fire. */
    private record BulletMarks(boolean glassWhole, boolean noFire) {}

    /**
     * An incendiary bullet hits glass, then the top of the floor, through TacZ's own {@code onHitBlock}. TacZ shatters the
     * glass and lights the floor (both its configs are on by default) unless a match holds them back.
     */
    private static BulletMarks incendiaryShot(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos glass = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        helper.getLevel().setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        BlockPos floor = helper.absolutePos(new BlockPos(2, ROOM_Y - 1, 2));
        BlockPos aboveFloor = floor.above();
        helper.assertTrue(helper.getLevel().getBlockState(floor).isFaceSturdy(helper.getLevel(), floor, Direction.UP)
                && helper.getLevel().getBlockState(aboveFloor).isAir(), "the floor should take a fire");

        EntityKineticBullet bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, helper.getLevel());
        ResourceLocation ak = ResourceLocation.tryBuild("tacz", "ak47");
        for (String name : List.of("ammoId", "gunId", "gunDisplayId")) {
            set(bullet, name, ak);
        }
        set(bullet, "igniteBlock", true);
        Method onHitBlock = EntityKineticBullet.class.getDeclaredMethod("onHitBlock", BlockHitResult.class, Vec3.class, Vec3.class);
        onHitBlock.setAccessible(true);
        for (BlockPos target : List.of(glass, floor)) {
            Vec3 top = Vec3.upFromBottomCenterOf(target, 1);
            onHitBlock.invoke(bullet, new BlockHitResult(top, Direction.UP, target, false), top.add(0, 1, 0), top);
        }
        helper.assertTrue(bullet.isRemoved(), "the hit should run to its end, where the bullet is removed");
        return new BulletMarks(helper.getLevel().getBlockState(glass).is(Blocks.GLASS), helper.getLevel().getBlockState(aboveFloor).isAir());
    }

    private static void set(EntityKineticBullet bullet, String field, Object value) throws ReflectiveOperationException {
        Field f = EntityKineticBullet.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(bullet, value);
    }

    @GameTest(template = "floor", batch = "terrain_no_game")
    public static void outsideAMatchExplosionsAndGunsBreakTerrainAsUsual(GameTestHelper helper) {
        helper.assertTrue(vanillaBlast(helper) == 0, "a blast outside a match should break all 6 glass blocks");
        helper.assertTrue(gunBlast(helper) < 6, "a gun's blast outside a match should break glass");
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "terrain_no_game")
    public static void outsideAMatchBulletsBreakGlassAndStartFires(GameTestHelper helper) throws ReflectiveOperationException {
        BulletMarks marks = incendiaryShot(helper);
        helper.assertTrue(!marks.glassWhole() && !marks.noFire(), "outside a match a bullet should break glass and light the floor: " + marks);
        helper.succeed();
    }

    /** A lobby isn't a match: the map makers' world behaves as usual until the game starts. */
    @GameTest(template = "floor", batch = "terrain_lobby")
    public static void aGameStillInItsLobbyLeavesExplosionsAlone(GameTestHelper helper) {
        GameManager.Active active = game(helper, false);
        try {
            helper.assertTrue(gunBlast(helper) < 6, "a gun's blast in the lobby should break glass");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "terrain_match_blast")
    public static void inAMatchNoExplosionBreaksTerrain(GameTestHelper helper) {
        GameManager.Active active = game(helper, true);
        try {
            helper.assertTrue(vanillaBlast(helper) == 6, "a blast in a match broke glass");
            helper.assertTrue(gunBlast(helper) == 6, "a gun's blast in a match broke glass");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "terrain_match_bullet")
    public static void inAMatchBulletsBreakNoGlassAndStartNoFire(GameTestHelper helper) throws ReflectiveOperationException {
        GameManager.Active active = game(helper, true);
        try {
            BulletMarks marks = incendiaryShot(helper);
            helper.assertTrue(marks.glassWhole() && marks.noFire(), "in a match a bullet should leave the glass and the floor: " + marks);
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }
}
