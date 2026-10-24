package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import dev.candycanecazoo.hellstorm.mob.AiInjector;
import dev.candycanecazoo.hellstorm.mob.Dismemberment;
import dev.candycanecazoo.hellstorm.mob.Limbs;
import dev.candycanecazoo.hellstorm.mob.MobProfile;
import dev.candycanecazoo.hellstorm.mob.MobScale;
import dev.candycanecazoo.hellstorm.mob.Runners;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hellhounds spawn half again a wolf's size, a scaled crawler lies prone at its scale, and runners walk as fast as a walking player. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobSizeAndSpeedGameTests {
    private MobSizeAndSpeedGameTests() {}

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    @GameTest(template = "floor", batch = "mob_scale")
    public static void aHellhoundIsOneAndAHalfTimesAWolf(GameTestHelper helper) {
        DismembermentGameTests.Range range = DismembermentGameTests.range(helper, "hound_size", List.of());
        try {
            UUID id = range.active().world().spawnZombie(range.marker(), new GameWorld.ZombieSpec(Hellstorm.id("hellhound"), 30, 0.23)).orElseThrow();
            Wolf hound = (Wolf) helper.getLevel().getEntity(id);
            Wolf wolf = helper.spawn(EntityType.WOLF, 2, 2, 2);
            helper.assertTrue(near(MobScale.of(hound), 1.5), "the hellhound profile's scale is 1.5, not " + MobScale.of(hound));
            helper.assertTrue(near(hound.getBbWidth(), wolf.getBbWidth() * 1.5) && near(hound.getBbHeight(), wolf.getBbHeight() * 1.5),
                    "a hound's hitbox is " + hound.getBbWidth() + " x " + hound.getBbHeight() + ", a wolf's " + wolf.getBbWidth() + " x " + wolf.getBbHeight());
            helper.assertTrue(near(hound.getEyeHeight(), wolf.getEyeHeight() * 1.5), "its eyes are at " + hound.getEyeHeight() + ", a wolf's at " + wolf.getEyeHeight());
            CompoundTag saved = hound.saveWithoutId(new CompoundTag());
            Wolf loaded = EntityType.WOLF.create(helper.getLevel());
            loaded.load(saved);
            loaded.refreshDimensions();
            helper.assertTrue(near(loaded.getBbHeight(), hound.getBbHeight()), "a saved hound loads at its size, not " + loaded.getBbHeight());
            helper.assertTrue(near(wolf.getBbHeight(), EntityType.WOLF.getHeight()), "a plain wolf keeps its size");
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "mob_scale_crawl")
    public static void aScaledCrawlerLiesProneAtItsScale(GameTestHelper helper) {
        DismembermentGameTests.Range range = DismembermentGameTests.range(helper, "scaled_crawl", List.of());
        try {
            Zombie zombie = DismembermentGameTests.spawn(helper, range, "zombie");
            MobScale.set(zombie, 2f);
            helper.assertTrue(near(zombie.getBbHeight(), EntityType.ZOMBIE.getHeight() * 2), "a zombie at scale 2 stands " + zombie.getBbHeight() + " tall");
            Limbs.crawl(zombie, Dismemberment.CRAWL_SPEED);
            helper.assertTrue(near(zombie.getBbHeight(), Limbs.PRONE.height * 2) && near(zombie.getBbWidth(), Limbs.PRONE.width * 2),
                    "crawling at scale 2, its hitbox is " + zombie.getBbWidth() + " x " + zombie.getBbHeight());
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "mob_runner")
    public static void aRunnerIsRaisedToTheRunSpeedOnceAndACrawlerRunsAtEightTenthsOfIt(GameTestHelper helper) {
        DismembermentGameTests.Range range = DismembermentGameTests.range(helper, "runners", List.of());
        try {
            Zombie walker = DismembermentGameTests.spawn(helper, range, "zombie");
            Zombie runner = DismembermentGameTests.spawn(helper, range, "zombie");
            Zombie crawler = DismembermentGameTests.spawn(helper, range, "zombie");
            double walk = walker.getAttributeValue(Attributes.MOVEMENT_SPEED);
            range.active().world().makeRunner(runner.getUUID());
            range.active().world().makeRunner(runner.getUUID());
            helper.assertTrue(Runners.runs(runner) && !Runners.runs(walker), "only the one made a runner runs");
            helper.assertTrue(near(walk, 0.23) && near(runner.getAttributeValue(Attributes.MOVEMENT_SPEED), Runners.SPEED),
                    "a runner made twice has the run speed, not " + runner.getAttributeValue(Attributes.MOVEMENT_SPEED));
            Limbs.crawl(crawler, Dismemberment.CRAWL_SPEED);
            Runners.run(crawler);
            helper.assertTrue(near(crawler.getAttributeValue(Attributes.MOVEMENT_SPEED), Runners.SPEED * Dismemberment.CRAWL_SPEED),
                    "a running crawler has 0.8 of the run speed, not " + crawler.getAttributeValue(Attributes.MOVEMENT_SPEED));
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    /** Blocks a second a player walks on flat ground: 0.1 speed, times 0.98 input, over 1 - 0.546 friction, 20 ticks a second. */
    private static final double WALKING_PLAYER = 0.1 * 0.98 / (1 - 0.6 * 0.91) * 20;

    /**
     * On a straight 64-block track, a round 1 runner walks within 5% of a walking player's 4.32 blocks a second, a round 1
     * walker is slower, and a runner whose round already makes it faster than that (sprinting, from round 9) keeps its speed.
     */
    @GameTest(template = "floor", timeoutTicks = 200, batch = "mob_runner_track")
    public static void aRunnerWalksAsFastAsAWalkingPlayer(GameTestHelper helper) {
        double[] roundSpeeds = {0.23, 0.23, 0.32};
        boolean[] runs = {false, true, true};
        int length = 64;
        Runnable clear = FarTargetGameTests.build(helper, 2 * roundSpeeds.length + 1, length, (x, z) -> x % 2 == 0 || z == 0 || z == length - 1);
        MobProfile profile = new MobProfile(EntityType.getKey(EntityType.ZOMBIE), 10, 1, Optional.empty(), MobProfile.Ai.REPLACE, Optional.empty(),
                1, 1, 0, false, MobProfile.Navigation.VANILLA, 1.0);
        Zombie[] zombies = new Zombie[roundSpeeds.length];
        for (int i = 0; i < zombies.length; i++) {
            zombies[i] = helper.spawn(EntityType.ZOMBIE, new BlockPos(2 * i + 1, TestLayout.ROOM_Y, 1));
            zombies[i].setBaby(false);
            AiInjector.inject(zombies[i], profile, new AiInjector.Stats(20, roundSpeeds[i], 0f), List::of);
            if (runs[i]) {
                Runners.run(zombies[i]);
            }
        }
        double[] from = new double[zombies.length];
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            for (int i = 0; i < zombies.length; i++) {
                Vec3 end = helper.absoluteVec(new Vec3(2 * i + 1.5, TestLayout.ROOM_Y, length - 2));
                zombies[i].getMoveControl().setWantedPosition(end.x, end.y, end.z, 1.0);
                if (ticks[0] == 20) {
                    from[i] = zombies[i].getZ();
                }
            }
        });
        helper.runAfterDelay(120, () -> {
            double[] perSecond = new double[zombies.length];
            for (int i = 0; i < zombies.length; i++) {
                perSecond[i] = (zombies[i].getZ() - from[i]) / ((ticks[0] - 20) / 20.0);
            }
            clear.run();
            String speeds = String.format("walker %.3f, runner %.3f, sprint-round runner %.3f blocks/s, walking player %.3f",
                    perSecond[0], perSecond[1], perSecond[2], WALKING_PLAYER);
            Hellstorm.LOG.info("Measured ground speeds: {}", speeds);
            helper.assertTrue(Math.abs(perSecond[1] / WALKING_PLAYER - 1) < 0.05, "a runner should walk as fast as a player: " + speeds);
            helper.assertTrue(perSecond[0] < perSecond[1], "a walker should be slower than a runner: " + speeds);
            helper.assertTrue(near(zombies[2].getAttributeValue(Attributes.MOVEMENT_SPEED), 0.32) && perSecond[2] >= perSecond[1],
                    "a runner already faster than the run speed keeps its own: " + speeds);
            helper.succeed();
        });
    }
}
