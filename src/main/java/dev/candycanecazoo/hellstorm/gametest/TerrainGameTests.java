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
 * Guns never change terrain, and nothing does while a game runs. The tests without a game get their own batch, since a game
 * registered by a test in the same batch would cover their level too.
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

    /** Without a game, a vanilla blast breaks the shell, so the tests below that keep it are measuring something. */
    @GameTest(template = "floor", batch = "terrain_no_game")
    public static void outsideAGameAnExplosionBreaksTheShell(GameTestHelper helper) {
        List<BlockPos> shell = glassShell(helper);
        Vec3 at = center(helper);
        helper.getLevel().explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
        helper.assertTrue(glassLeft(helper, shell) == 0, glassLeft(helper, shell) + " of 6 glass blocks survived a blast");
        helper.succeed();
    }

    /** TacZ's own explosion call with block damage asked for, as a rocket makes on impact. Outside a game too. */
    @GameTest(template = "floor", batch = "terrain_no_game")
    public static void aGunExplosionLeavesTheTerrain(GameTestHelper helper) {
        List<BlockPos> shell = glassShell(helper);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, ROOM_Y, 1));
        ExplodeUtil.createExplosion(shooter, shooter, 10f, 3f, false, true, center(helper));
        helper.assertTrue(glassLeft(helper, shell) == 6, "a gun's blast broke " + (6 - glassLeft(helper, shell)) + " of 6 glass blocks");
        helper.succeed();
    }

    /**
     * A bullet with incendiary rounds hits glass, then the top of the floor. TacZ would shatter the glass and light a fire on
     * the floor (both of its configs are on by default). The hit runs through TacZ's own {@code onHitBlock}.
     */
    @GameTest(template = "floor", batch = "terrain_no_game")
    public static void bulletsBreakNoGlassAndStartNoFire(GameTestHelper helper) throws ReflectiveOperationException {
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
        helper.assertTrue(helper.getLevel().getBlockState(glass).is(Blocks.GLASS), "a bullet broke glass");
        helper.assertTrue(helper.getLevel().getBlockState(aboveFloor).isAir(), "a bullet left " + helper.getLevel().getBlockState(aboveFloor));
        helper.succeed();
    }

    private static void set(EntityKineticBullet bullet, String field, Object value) throws ReflectiveOperationException {
        Field f = EntityKineticBullet.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(bullet, value);
    }

    /** The map is immutable during play: a vanilla blast in a game's level breaks nothing either. */
    @GameTest(template = "floor", batch = "game_terrain")
    public static void inAGameAnExplosionLeavesTheTerrain(GameTestHelper helper) {
        List<BlockPos> shell = glassShell(helper);
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_blast"),
                List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, 0)), helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8)))), true));
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()),
                List.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 1))), id -> null);
        try {
            Vec3 at = center(helper);
            helper.getLevel().explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(glassLeft(helper, shell) == 6, "a blast in a game broke " + (6 - glassLeft(helper, shell)) + " of 6 glass blocks");
        helper.succeed();
    }
}
