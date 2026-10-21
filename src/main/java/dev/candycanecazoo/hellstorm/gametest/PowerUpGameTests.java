package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxes;
import dev.candycanecazoo.hellstorm.game.ForgeGameWorld;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GameMap;
import dev.candycanecazoo.hellstorm.game.GamePhase;
import dev.candycanecazoo.hellstorm.game.TimedEffects;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.powerup.PowerUpDef;
import dev.candycanecazoo.hellstorm.powerup.PowerUpDefs;
import dev.candycanecazoo.hellstorm.powerup.PowerUpEffect;
import dev.candycanecazoo.hellstorm.powerup.PowerUpEntity;
import dev.candycanecazoo.hellstorm.powerup.PowerUpRegistries;
import dev.candycanecazoo.hellstorm.powerup.PowerUps;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.hellstorm.gametest.PurchaseGameTests.points;
import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Each power-up's effect, picked up through the same path the floating pickup uses. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerUpGameTests {
    private static final String MAGAZINE = "GunCurrentAmmoCount";
    private static final String RESERVE = "DummyAmmo";

    private PowerUpGameTests() {}

    private static ResourceLocation id(String path) {
        return Hellstorm.id(path);
    }

    private static boolean collect(GameManager.Active active, ServerPlayer player, String powerUp) {
        return PowerUps.collect(active, player, id(powerUp), player.position());
    }

    /** A one-room game with a spawn marker, started, so zombies arrive after the countdown. */
    private static GameManager.Active fightingGame(GameTestHelper helper, ServerPlayer player) {
        BlockPos min = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        BlockPos max = helper.absolutePos(new BlockPos(8, ROOM_Y + 3, 8));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("gametest_powerups"), List.of(BlockBox.of(min, max)), true))
                .withFeature(helper.absolutePos(new BlockPos(7, ROOM_Y, 7)), FeatureKind.SPAWN);
        BlockPos stand = helper.absolutePos(new BlockPos(1, ROOM_Y, 1));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        active.game().start();
        return active;
    }

    private static List<Zombie> zombies(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(9, ROOM_Y + 4, 9))).inflate(2);
        return helper.getLevel().getEntitiesOfClass(Zombie.class, area, z -> z.isAlive() && z.getTags().contains(ForgeGameWorld.MOB_TAG));
    }

    @GameTest(template = "floor", batch = "powerup_max_ammo")
    public static void maxAmmoRefillsEveryGunOfEveryPlayer(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_powerup_ammo");
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        try {
            ItemStack held = TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "ak47"), 2).orElseThrow();
            ItemStack stowed = TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "glock_17"), 2).orElseThrow();
            for (ItemStack gun : List.of(held, stowed)) {
                gun.getTag().putInt(MAGAZINE, 0);
                gun.getTag().putInt(RESERVE, 0);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.getInventory().setItem(5, stowed);
            helper.assertTrue(collect(active, player, "max_ammo"), "Max Ammo wasn't collected");
            for (ItemStack gun : List.of(player.getMainHandItem(), stowed)) {
                helper.assertTrue(gun.getTag().getInt(MAGAZINE) > 0 && gun.getTag().getInt(RESERVE) > 0, "a gun is still empty: " + gun.getTag());
            }
            helper.assertTrue(events.contains(new GameEvent.PowerUpCollected(id("max_ammo"), player.getUUID())), "no powerup_collected event");
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "powerup_double_points")
    public static void doublePointsDoublesWhatPlayersEarnAndShowsATimer(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_powerup_double");
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        try {
            int before = points(active, player);
            active.game().onRepair(player.getUUID());
            int single = points(active, player) - before;
            helper.assertTrue(collect(active, player, "double_points"), "Double Points wasn't collected");
            helper.assertTrue(active.game().timedActive(TimedEffects.DOUBLE_POINTS), "Double Points isn't running");
            helper.assertTrue(active.game().status(player.getUUID()).timers().stream().anyMatch(t -> t.name().equals("powerup.hellstorm.double_points")),
                    "no Double Points timer on the HUD");
            int mid = points(active, player);
            active.game().onRepair(player.getUUID());
            helper.assertTrue(points(active, player) - mid == 2 * single, "a repair paid " + (points(active, player) - mid) + ", expected " + 2 * single);
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "powerup_carpenter")
    public static void carpenterBoardsEveryWindowAndPaysEveryone(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(0, ROOM_Y, 0));
        BlockPos max = helper.absolutePos(new BlockPos(8, ROOM_Y + 2, 8));
        ZoneId zone = new ZoneId("gametest_carpenter");
        List<WindowDef> windows = List.of(
                new WindowDef("left", BlockBox.of(helper.absolutePos(new BlockPos(2, ROOM_Y, 9)), helper.absolutePos(new BlockPos(2, ROOM_Y + 1, 9))),
                        Direction.SOUTH, 6, zone),
                new WindowDef("right", BlockBox.of(helper.absolutePos(new BlockPos(6, ROOM_Y, 9)), helper.absolutePos(new BlockPos(7, ROOM_Y, 9))),
                        Direction.SOUTH, 4, zone));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(zone, List.of(BlockBox.of(min, max)), true));
        for (WindowDef window : windows) {
            map = map.withWindow(window);
        }
        ServerPlayer player = player(helper, "pap_powerup_carpenter");
        BlockPos stand = helper.absolutePos(new BlockPos(4, ROOM_Y, 4));
        player.moveTo(stand.getCenter());
        GameManager.Active active = GameManager.register(helper.getLevel(), GameMap.of(map, Set.of()), List.of(stand),
                id -> id.equals(player.getUUID()) ? player : null);
        active.game().join(player.getUUID());
        PurchaseGameTests.play(active);
        try {
            windows.forEach(w -> WindowOps.setBoards(helper.getLevel(), w, 0));
            int before = points(active, player);
            helper.assertTrue(collect(active, player, "carpenter"), "Carpenter wasn't collected");
            for (WindowDef window : windows) {
                helper.assertTrue(WindowOps.boards(helper.getLevel(), window) == window.maxBoards(),
                        window.id() + " has " + WindowOps.boards(helper.getLevel(), window) + " of " + window.maxBoards() + " boards");
            }
            int bonus = active.game().settings().powerUps().carpenterBonus();
            helper.assertTrue(points(active, player) == before + bonus, "expected the " + bonus + " bonus, got " + (points(active, player) - before));
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }

    /** Fire Sale opens every box location at the Fire Sale price, and closes them again when its timer runs out. */
    @GameTest(template = "floor", timeoutTicks = 600, batch = "powerup_fire_sale")
    public static void fireSaleLowersTheBoxCostUntilItRunsOut(GameTestHelper helper) {
        List<BlockPos> boxes = new ArrayList<>();
        for (int x : new int[] {2, 6}) {
            BlockPos at = helper.absolutePos(new BlockPos(x, ROOM_Y, 2));
            MysteryBoxBlock.place(helper.getLevel(), at, net.minecraft.core.Direction.EAST);
            boxes.add(at);
        }
        ServerPlayer player = player(helper, "pap_powerup_fire_sale");
        GameManager.Active active = PurchaseGameTests.game(helper, player, boxes.toArray(BlockPos[]::new));
        int fireSaleCost = active.game().settings().box().fireSaleCost();
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (!started[0] && active.game().phase() instanceof GamePhase.RoundActive) {
                started[0] = true;
                PowerUpDef fireSale = PowerUpDefs.get(id("fire_sale")).orElseThrow();
                helper.assertTrue(fireSale.effect() == PowerUpEffect.FIRE_SALE, "the default fire_sale is a Fire Sale");
                // A short timer, so the test sees it run out.
                PowerUps.apply(active, new PowerUpDef(fireSale.name(), fireSale.weight(), 20, fireSale.item(), fireSale.effect()));
                for (BlockPos box : boxes) {
                    MysteryBoxBlock.Entity entity = (MysteryBoxBlock.Entity) helper.getLevel().getBlockEntity(box);
                    if (helper.getLevel().getBlockState(box).getValue(MysteryBoxBlock.BOX) != MysteryBoxBlock.Presence.HERE || entity.price() != fireSaleCost) {
                        GameManager.end(active);
                        helper.fail("box at " + box + " isn't open at the Fire Sale price " + fireSaleCost + " (price " + entity.price() + ")");
                    }
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(started[0], "the round hasn't started");
            helper.assertFalse(active.game().timedActive(TimedEffects.FIRE_SALE), "the Fire Sale is still running");
            helper.assertFalse(MysteryBoxes.fireSale(active), "the box still thinks there's a Fire Sale");
            long open = boxes.stream().filter(b -> helper.getLevel().getBlockState(b).getValue(MysteryBoxBlock.BOX) == MysteryBoxBlock.Presence.HERE).count();
            GameManager.end(active);
            helper.assertTrue(open == 1, "after the Fire Sale only the home location should be open, " + open + " are");
        });
    }

    @GameTest(template = "floor", timeoutTicks = 600, batch = "powerup_insta_kill")
    public static void instaKillKillsAZombieInOneHit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_powerup_insta");
        GameManager.Active active = fightingGame(helper, player);
        helper.succeedWhen(() -> {
            List<Zombie> zombies = zombies(helper);
            helper.assertTrue(!zombies.isEmpty(), "no game zombie yet");
            Zombie zombie = zombies.get(0);
            int before = points(active, player);
            boolean collected = collect(active, player, "insta_kill");
            zombie.hurt(player.damageSources().playerAttack(player), 1f);
            boolean dead = !zombie.isAlive();
            int earned = points(active, player) - before;
            int kill = active.game().settings().economy().meleeKill();
            GameManager.end(active);
            helper.assertTrue(collected, "Insta-Kill wasn't collected");
            helper.assertTrue(dead, "a 1 damage punch didn't kill a zombie under Insta-Kill");
            helper.assertTrue(earned == kill, "the kill paid " + earned + ", expected " + kill);
        });
    }

    @GameTest(template = "floor", timeoutTicks = 600, batch = "powerup_nuke")
    public static void nukeKillsEveryZombieForTheBonusOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_powerup_nuke");
        GameManager.Active active = fightingGame(helper, player);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombies(helper).size() >= 2, "waiting for two game zombies, have " + zombies(helper).size());
            int before = points(active, player);
            boolean collected = collect(active, player, "nuke");
            int left = zombies(helper).size();
            int tracked = active.game().mobs().size();
            int earned = points(active, player) - before;
            int bonus = active.game().settings().powerUps().nukeBonus();
            long kills = events.stream().filter(e -> e instanceof GameEvent.ZombieKilled k && k.killer().isEmpty()).count();
            GameManager.end(active);
            helper.assertTrue(collected, "Nuke wasn't collected");
            helper.assertTrue(left == 0 && tracked == 0, left + " zombies still standing, " + tracked + " still tracked");
            helper.assertTrue(earned == bonus, "a Nuke paid " + earned + ", expected only the " + bonus + " bonus");
            helper.assertTrue(kills >= 2, "the zombies should die as kills nobody made, saw " + kills);
        });
    }

    /** A drop puts a pickup in the world; walking into it collects it once and removes it. */
    @GameTest(template = "floor", batch = "powerup_pickup")
    public static void walkingIntoAPickupCollectsItOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pap_powerup_pickup");
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        List<GameEvent> events = new ArrayList<>();
        active.game().events().subscribe(events::add);
        BlockPos at = helper.absolutePos(new BlockPos(6, ROOM_Y, 6));
        PowerUps.drop(helper.getLevel(), active.game().settings().powerUps(), id("double_points"), Vec3.atBottomCenterOf(at));
        AABB area = new AABB(at).inflate(3);
        helper.runAfterDelay(2, () -> {
            List<PowerUpEntity> pickups = helper.getLevel().getEntities(PowerUpRegistries.POWER_UP.get(), area, e -> true);
            if (pickups.size() != 1) {
                GameManager.end(active);
                helper.fail("expected one pickup, found " + pickups.size());
            }
            player.moveTo(pickups.get(0).position());
        });
        helper.runAfterDelay(6, () -> {
            boolean gone = helper.getLevel().getEntities(PowerUpRegistries.POWER_UP.get(), area, e -> true).isEmpty();
            long collected = events.stream().filter(e -> e instanceof GameEvent.PowerUpCollected).count();
            boolean running = active.game().timedActive(TimedEffects.DOUBLE_POINTS);
            GameManager.end(active);
            helper.assertTrue(gone, "the pickup is still there");
            helper.assertTrue(collected == 1, "collected " + collected + " times");
            helper.assertTrue(running, "Double Points didn't start");
            helper.succeed();
        });
    }
}
