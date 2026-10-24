package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import dev.candycanecazoo.hellstorm.mob.Dismemberment;
import dev.candycanecazoo.hellstorm.mob.Limbs;
import dev.candycanecazoo.hellstorm.mob.MobScale;
import dev.candycanecazoo.hellstorm.mob.Runners;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Hellhounds spawn half again a wolf's size, a scaled crawler lies prone at its scale, and runners move 1.2 times as fast. */
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
    public static void runnersMoveOnePointTwoTimesAsFastCrawlersIncluded(GameTestHelper helper) {
        DismembermentGameTests.Range range = DismembermentGameTests.range(helper, "runners", List.of());
        try {
            Zombie walker = DismembermentGameTests.spawn(helper, range, "zombie");
            Zombie runner = DismembermentGameTests.spawn(helper, range, "zombie");
            Zombie crawler = DismembermentGameTests.spawn(helper, range, "zombie");
            double walk = walker.getAttributeValue(Attributes.MOVEMENT_SPEED);
            range.active().world().makeRunner(runner.getUUID());
            range.active().world().makeRunner(runner.getUUID());
            helper.assertTrue(Runners.runs(runner) && !Runners.runs(walker), "only the one made a runner runs");
            helper.assertTrue(near(runner.getAttributeValue(Attributes.MOVEMENT_SPEED) / walk, 1.2),
                    "a runner made twice moves at 1.2 of a walker, not " + runner.getAttributeValue(Attributes.MOVEMENT_SPEED) / walk);
            Limbs.crawl(crawler, Dismemberment.CRAWL_SPEED);
            Runners.run(crawler);
            helper.assertTrue(near(crawler.getAttributeValue(Attributes.MOVEMENT_SPEED) / walk, 0.96),
                    "a running crawler moves at 0.8 x 1.2 = 0.96 of a walker, not " + crawler.getAttributeValue(Attributes.MOVEMENT_SPEED) / walk);
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }
}
