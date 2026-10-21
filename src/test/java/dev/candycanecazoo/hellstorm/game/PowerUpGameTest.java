package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.powerup.PowerUpDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Power-up drops and Double Points in the game loop, against a fake world. */
class PowerUpGameTest {
    private static final ResourceLocation NUKE = ResourceLocation.tryBuild("hellstorm", "nuke");
    private static final MobRoster ROSTER = new MobRoster(List.of(new MobRoster.Entry(ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty())));

    private FakeWorld world;
    private List<GameEvent> seen;
    private UUID alice;

    private static GameSettings settings(double dropChance, int cap) {
        return settings(dropChance, cap, Integer.MAX_VALUE, 1.0);
    }

    private static GameSettings settings(double dropChance, int cap, int increment, double growth) {
        GameSettings d = GameSettings.DEFAULT;
        return new GameSettings(new GameSettings.Rounds(16, 0, 0.15, 0.004, 100, 100, 0.23, 0.23, 0.32, 24, 1, 1.0, 3, 2, 2),
                new GameSettings.Spawning(1, 24.0, 0.0), d.economy(), d.players(), d.windows(), d.doors(), d.box(), d.pap(), d.downs(),
                new GameSettings.PowerUps(dropChance, cap, 600, 100, 400, 200, increment, growth), d.traps(), d.waveEvents());
    }

    private Game game(GameSettings settings) {
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("a"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withFeature(new BlockPos(5, 0, 5), FeatureKind.SPAWN);
        GameEvents events = new GameEvents();
        events.subscribe(seen::add);
        Game game = new Game(settings, GameMap.of(map, Set.of()), ROSTER, RandomSource.create(7), events,
                new SurvivalContent(new PowerUpDrops.Table(List.of(new PowerUpDrops.Entry(NUKE, 1))), java.util.Map.of()));
        assertTrue(game.join(alice));
        assertTrue(game.start());
        tick(game, 30);
        return game;
    }

    private void tick(Game game, int times) {
        for (int i = 0; i < times; i++) {
            game.tick(world);
        }
    }

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        seen = new ArrayList<>();
        alice = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(2, 0, 2));
    }

    @Test
    void playerKillsDropUpToTheRoundsCapAndTheCapResetsNextRound() {
        Game game = game(settings(1.0, 2));
        List<UUID> mobs = new ArrayList<>(game.mobs());
        assertEquals(4, mobs.size());
        game.onMobDied(mobs.get(0), Optional.empty(), false, false, new BlockPos(1, 0, 1));
        game.onMobDied(mobs.get(1), Optional.of(alice), false, false, new BlockPos(2, 0, 2));
        assertEquals(List.of(), world.drops, "drops wait for the next tick");
        game.onMobDied(mobs.get(2), Optional.of(alice), false, false, new BlockPos(3, 0, 3));
        game.onMobDied(mobs.get(3), Optional.of(alice), false, false, new BlockPos(4, 0, 4));
        tick(game, 1);
        assertEquals(List.of(NUKE, NUKE), world.drops, "a kill nobody made drops nothing, and the cap stops the third");
        assertEquals(2, seen.stream().filter(e -> e instanceof GameEvent.PowerUpSpawned).count());
        assertTrue(seen.contains(new GameEvent.PowerUpSpawned(NUKE, new BlockPos(2, 0, 2))));

        tick(game, 10);
        assertEquals(2, ((GamePhase.RoundActive) game.phase()).round());
        tick(game, 30);
        game.onMobDied(game.mobs().iterator().next(), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, 1);
        assertEquals(3, world.drops.size(), "round 2 can drop again");
    }

    /** Kills are worth 60. The first drop is due at 100 team points; each drop doubles the gap, then measures the next from there. */
    @Test
    void theTeamsPointsTriggerDropsAtAGrowingThresholdWithoutAnyChance() {
        Game game = game(settings(0.0, 10, 100, 2.0));
        List<UUID> mobs = new ArrayList<>(game.mobs());
        assertEquals(4, mobs.size());
        game.onMobDied(mobs.get(0), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, 1);
        assertEquals(0, world.drops.size(), "60 points: not due yet");
        game.onMobDied(mobs.get(1), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, 1);
        assertEquals(1, world.drops.size(), "120 points passed 100");
        game.onMobDied(mobs.get(2), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, 1);
        assertEquals(1, world.drops.size(), "180: the next is due at 120 + 200 = 320");
        game.onMobDied(mobs.get(3), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, 1);
        assertEquals(1, world.drops.size(), "240: still short of 320, since the gap grew before the threshold moved");
    }

    @Test
    void doublePointsDoublesHitsAndKillsWhileItRuns() {
        Game game = game(settings(0.0, 4));
        List<UUID> mobs = new ArrayList<>(game.mobs());
        int start = game.player(alice).orElseThrow().points();
        game.startTimed(TimedEffects.DOUBLE_POINTS, "powerup.hellstorm.double_points", 5);
        game.onMobHit(mobs.get(0), alice);
        game.onMobDied(mobs.get(0), Optional.of(alice), false, false, BlockPos.ZERO);
        GameSettings.Economy economy = GameSettings.DEFAULT.economy();
        assertEquals(start + 2 * (economy.hit() + economy.kill()), game.player(alice).orElseThrow().points());
        tick(game, 5);
        assertFalse(game.timedActive(TimedEffects.DOUBLE_POINTS));
        int before = game.player(alice).orElseThrow().points();
        game.onMobDied(mobs.get(1), Optional.of(alice), false, false, BlockPos.ZERO);
        assertEquals(before + economy.kill(), game.player(alice).orElseThrow().points(), "back to normal once it ends");
    }

    @Test
    void collectingCountsOnlyForAlivePlayersAndBonusesGoToEveryone() {
        Game game = game(settings(0.0, 4));
        assertTrue(game.collectPowerUp(NUKE, alice));
        assertTrue(seen.contains(new GameEvent.PowerUpCollected(NUKE, alice)));
        assertFalse(game.collectPowerUp(NUKE, UUID.randomUUID()), "someone not in the game");
        int start = game.player(alice).orElseThrow().points();
        game.awardEveryone(400);
        assertEquals(start + 400, game.player(alice).orElseThrow().points());
        game.stop(world);
        assertFalse(game.collectPowerUp(NUKE, alice), "nothing counts once the game is over");
    }
}
