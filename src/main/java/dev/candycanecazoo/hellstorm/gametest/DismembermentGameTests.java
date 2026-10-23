package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.CommonConfig;
import dev.candycanecazoo.hellstorm.game.ForgeGameWorld;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.mob.BodyPart;
import dev.candycanecazoo.hellstorm.mob.Dismemberment;
import dev.candycanecazoo.hellstorm.mob.Limbs;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Shot limbs come off, a lost leg makes a crawler, and the crawler profile crawls from the start. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DismembermentGameTests {
    private DismembermentGameTests() {}

    private record Range(GameManager.Active active, BlockPos marker, BlockPos min, BlockPos max) {}

    private static Range range(GameTestHelper helper, String name, List<ServerPlayer> players) {
        BlockPos min = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        BlockPos max = helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8));
        BlockPos marker = helper.absolutePos(new BlockPos(7, ROOM_Y, 4));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_" + name), List.of(BlockBox.of(min, max)), true))
                .withFeature(marker, FeatureKind.SPAWN);
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()),
                List.of(helper.absolutePos(new BlockPos(1, ROOM_Y, 4))), id -> players.stream().filter(p -> p.getUUID().equals(id)).findFirst().orElse(null));
        players.forEach(p -> active.game().join(p.getUUID()));
        return new Range(active, marker, min, max);
    }

    private static Zombie spawn(GameTestHelper helper, Range range, String profile) {
        UUID id = range.active().world().spawnZombie(range.marker(), new GameWorld.ZombieSpec(Hellstorm.id(profile), 30, 0.23)).orElseThrow();
        Zombie zombie = (Zombie) helper.getLevel().getEntity(id);
        zombie.setNoAi(true);
        zombie.setYBodyRot(0);
        return zombie;
    }

    /** A point {@code right} blocks to the zombie's right and {@code up} over its feet. Facing south, its right is west. */
    private static Vec3 on(Zombie zombie, double right, double up) {
        return zombie.position().add(-right, up, 0);
    }

    @GameTest(template = "floor", batch = "dismember")
    public static void aShotArmComesOffOnceItHasTakenItsShareAndALegMakesACrawler(GameTestHelper helper) {
        Range range = range(helper, "dismember", List.of());
        try {
            Zombie zombie = spawn(helper, range, "zombie");
            float share = Dismemberment.LIMB_SHARE * zombie.getMaxHealth();
            double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, 0.25, 1.1), false, share / 2).isEmpty(), "half its share shouldn't take the arm off");
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, 0.25, 1.1), false, share / 2).equals(Optional.of(BodyPart.RIGHT_ARM)),
                    "the second half should take the right arm off");
            helper.assertTrue(Limbs.lost(zombie, BodyPart.RIGHT_ARM) && !Limbs.crawls(zombie), "an armless zombie still walks");
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, 0.05, 1.1), false, share).isEmpty(), "the body doesn't come off");
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, 0, 1.7), true, share).isEmpty(), "nor does the head");
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, -0.1, 0.3), false, share).equals(Optional.of(BodyPart.LEFT_LEG)), "the left leg comes off");
            helper.assertTrue(Limbs.crawls(zombie), "a zombie missing a leg crawls");
            helper.assertTrue(Math.abs(zombie.getBbHeight() - Limbs.PRONE.height) < 1e-4, "a crawler's hitbox is prone, " + zombie.getBbHeight() + " high");
            helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * Dismemberment.CRAWL_SPEED) < 1e-6,
                    "a crawler moves at " + Dismemberment.CRAWL_SPEED + " of " + speed + ", not " + zombie.getAttributeValue(Attributes.MOVEMENT_SPEED));
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, 0.1, 0.3), false, share).isEmpty(), "a crawler keeps what it has left");
            helper.assertTrue(zombie.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("ForgeData").getByte(Limbs.REMOVED)
                    == (BodyPart.RIGHT_ARM.bit() | BodyPart.LEFT_LEG.bit()), "the lost limbs are saved with the zombie");
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "dismember_off")
    public static void withDismembermentOffNothingComesOff(GameTestHelper helper) {
        Range range = range(helper, "dismember_off", List.of());
        CommonConfig.DISMEMBERMENT.set(false);
        try {
            Zombie zombie = spawn(helper, range, "zombie");
            float health = zombie.getMaxHealth();
            helper.assertTrue(Dismemberment.shot(zombie, on(zombie, -0.1, 0.3), false, health).isEmpty(), "a leg came off with dismemberment off");
            helper.assertTrue(Limbs.removed(zombie) == 0 && !Limbs.crawls(zombie), "the zombie should keep its limbs and stand");
        } finally {
            CommonConfig.DISMEMBERMENT.set(true);
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "dismember_profile")
    public static void theCrawlerProfileIsAZombieThatCrawlsFromTheStart(GameTestHelper helper) {
        Range range = range(helper, "crawler", List.of());
        try {
            Zombie walker = spawn(helper, range, "zombie");
            Zombie crawler = spawn(helper, range, "crawler");
            helper.assertTrue(Limbs.crawls(crawler) && Limbs.crawlSpeed(crawler).equals(Optional.of(0.5f)), "the crawler profile's tag crawls at 0.5");
            helper.assertTrue(Math.abs(crawler.getBbHeight() - Limbs.PRONE.height) < 1e-4, "its hitbox is prone, " + crawler.getBbHeight() + " high");
            double ratio = crawler.getAttributeValue(Attributes.MOVEMENT_SPEED) / walker.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(ratio - 0.5) < 1e-6, "it moves at half a walker's speed, not " + ratio);
            helper.assertTrue(Limbs.removed(crawler) == 0, "it has all its limbs");
        } finally {
            GameManager.end(range.active());
        }
        helper.succeed();
    }

    /**
     * A fake player shoots a real TacZ AK at a game zombie's legs: the hit point TacZ handles reaches the game, the leg
     * comes off, and the zombie crawls. Draw and shoot timers are wall-clock, so the test polls.
     */
    @GameTest(template = "floor", timeoutTicks = 4000, batch = "dismember_gun")
    public static void shootingAZombiesLegsWithATaczGunMakesItCrawl(GameTestHelper helper) {
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_dismember_gun");
        Range range = range(helper, "legs", List.of(player));
        PurchaseGameTests.play(range.active());
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "ak47"), 4).orElseThrow());
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        operator.initialData();
        operator.draw(player::getMainHandItem);
        AABB area = new AABB(range.min(), range.max()).inflate(2);
        Zombie[] target = {null};
        helper.onEachTick(() -> {
            // The round's other zombies would walk into the line of fire, so only the target is kept.
            helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG) && z != target[0] && target[0] != null)
                    .forEach(Zombie::discard);
            if (target[0] == null) {
                List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(ForgeGameWorld.MOB_TAG));
                if (zombies.isEmpty()) {
                    return;
                }
                target[0] = zombies.get(0);
                target[0].setNoAi(true);
                target[0].moveTo(helper.absolutePos(new BlockPos(6, ROOM_Y, 4)).getCenter().add(0, -0.5, 0));
                target[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(30);
            }
            target[0].setHealth(30);
            player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target[0].position().add(0, 0.35, 0));
            float pitch = player.getXRot(), yaw = player.getYRot();
            if (operator.shoot(() -> pitch, () -> yaw) == ShootResult.NO_AMMO) {
                operator.reload();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(target[0] != null && Limbs.crawls(target[0]), "waiting for a leg shot to make the zombie crawl");
            helper.assertTrue(Limbs.lost(target[0], BodyPart.LEFT_LEG) || Limbs.lost(target[0], BodyPart.RIGHT_LEG), "it crawls because it lost a leg");
            GameManager.end(range.active());
        });
    }
}
