package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The game loop against a fake world: four touching zones A-B-C-D along x, a spawn marker in each, A is the start. */
class GameTest {
    private static final GameSettings SETTINGS = new GameSettings(
            new GameSettings.Rounds(3, 1.0, 0.0, 0.1, 100, 0.23, 0.32, 24, 1, 3, 2, 2),
            new GameSettings.Spawning(1, 24.0, 0.0),
            GameSettings.DEFAULT.economy(),
            GameSettings.DEFAULT.players(),
            GameSettings.DEFAULT.windows(),
            GameSettings.DEFAULT.doors(),
            GameSettings.DEFAULT.box(),
            GameSettings.DEFAULT.pap());

    private static final MobRoster ROSTER = new MobRoster(List.of(
            new MobRoster.Entry(net.minecraft.resources.ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty())));

    private static final List<ZoneId> ZONES = List.of(new ZoneId("a"), new ZoneId("b"), new ZoneId("c"), new ZoneId("d"));

    private FakeWorld world;
    private GameEvents events;
    private List<GameEvent> seen;
    private UUID alice;

    private static BlockPos marker(int zone) {
        return new BlockPos(zone * 10 + 5, 0, 5);
    }

    private static GameMap map() {
        ZombiesMap map = ZombiesMap.EMPTY;
        for (int i = 0; i < ZONES.size(); i++) {
            map = map.withZone(new Zone(ZONES.get(i), List.of(new BlockBox(i * 10, 0, 0, i * 10 + 9, 3, 9)), i == 0))
                    .withFeature(marker(i), FeatureKind.SPAWN);
        }
        return GameMap.of(map, Set.of());
    }

    private Game newGame(GameSettings settings) {
        return new Game(settings, map(), ROSTER, RandomSource.create(7), events);
    }

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        events = new GameEvents();
        seen = new ArrayList<>();
        events.subscribe(seen::add);
        alice = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(2, 0, 2));
    }

    private static void tick(Game game, FakeWorld world, int times) {
        for (int i = 0; i < times; i++) {
            game.tick(world);
        }
    }

    private Game startedGame() {
        Game game = newGame(SETTINGS);
        assertTrue(game.join(alice));
        assertTrue(game.start());
        tick(game, world, 3);
        return game;
    }

    private void killAll(Game game, Optional<UUID> killer) {
        for (UUID mob : game.mobs()) {
            game.onMobDied(mob, killer, false, false, BlockPos.ZERO);
        }
    }

    @Test
    void startNeedsAPlayerAndTheLobby() {
        Game game = newGame(SETTINGS);
        assertFalse(game.start(), "no players");
        game.join(alice);
        assertTrue(game.start());
        assertFalse(game.start(), "already counting down");
    }

    @Test
    void countdownLeadsIntoRoundOne() {
        Game game = newGame(SETTINGS);
        game.join(alice);
        game.start();
        tick(game, world, 2);
        assertInstanceOf(GamePhase.Countdown.class, game.phase());
        tick(game, world, 1);
        GamePhase.RoundActive round = assertInstanceOf(GamePhase.RoundActive.class, game.phase());
        assertEquals(1, round.round());
        assertEquals(List.of(alice), world.respawned);
        assertEquals(List.of(new GameEvent.GameStart(List.of(alice)), new GameEvent.RoundStart(1)), seen);
    }

    @Test
    void aRoundSpawnsExactlyItsZombieCountThenEndsWhenAllAreDead() {
        Game game = startedGame();
        tick(game, world, 50);
        assertEquals(RoundPlan.zombieCount(SETTINGS.rounds(), 1, 1), world.spawned.size());
        assertEquals(3, game.mobs().size());
        assertInstanceOf(GamePhase.RoundActive.class, game.phase(), "round can't end with zombies alive");
        killAll(game, Optional.of(alice));
        tick(game, world, 1);
        assertEquals(new GamePhase.Intermission(2, 3), game.phase());
        assertTrue(seen.contains(new GameEvent.RoundEnd(1)));
        tick(game, world, 4);
        assertEquals(2, assertInstanceOf(GamePhase.RoundActive.class, game.phase()).round());
        tick(game, world, 50);
        assertEquals(3 + 4, world.spawned.size(), "round 2 spawns 3 + 1 more");
    }

    @Test
    void spawnsRespectTheMaxAliveCapAndTheDelay() {
        GameSettings capped = new GameSettings(
                new GameSettings.Rounds(10, 0, 0, 0.1, 100, 0.23, 0.32, 2, 5, 3, 0, 2),
                SETTINGS.spawning(), SETTINGS.economy(), SETTINGS.players(), SETTINGS.windows(), SETTINGS.doors(), SETTINGS.box(), SETTINGS.pap());
        Game game = newGame(capped);
        game.join(alice);
        game.start();
        tick(game, world, 1);
        assertInstanceOf(GamePhase.RoundActive.class, game.phase());
        assertEquals(0, world.spawned.size(), "the round opens on this tick");
        tick(game, world, 1);
        assertEquals(1, world.spawned.size(), "first spawn on the next tick");
        tick(game, world, 4);
        assertEquals(1, world.spawned.size(), "next spawn waits out the 5-tick delay");
        tick(game, world, 1);
        assertEquals(2, world.spawned.size(), "exactly 5 ticks after the first");
        tick(game, world, 40);
        assertEquals(2, world.spawned.size(), "max alive is 2");
        game.onMobDied(game.mobs().iterator().next(), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, world, 1);
        assertEquals(3, world.spawned.size(), "a kill frees a slot");
    }

    @Test
    void pointsForHitsAndEachKindOfKill() {
        Game game = startedGame();
        tick(game, world, 50);
        List<UUID> mobs = new ArrayList<>(game.mobs());
        int start = game.player(alice).orElseThrow().points();
        game.onMobHit(mobs.get(0), alice);
        game.onMobDied(mobs.get(0), Optional.of(alice), false, false, BlockPos.ZERO);
        game.onMobDied(mobs.get(1), Optional.of(alice), true, false, BlockPos.ZERO);
        game.onMobDied(mobs.get(2), Optional.of(alice), false, true, BlockPos.ZERO);
        assertEquals(start + 10 + 60 + 100 + 130, game.player(alice).orElseThrow().points());
        assertEquals(3, game.player(alice).orElseThrow().kills());
        assertEquals(1, game.player(alice).orElseThrow().headshots());
    }

    @Test
    void killsNobodyCausedAndUnknownMobsAwardNothing() {
        Game game = startedGame();
        tick(game, world, 50);
        int start = game.player(alice).orElseThrow().points();
        UUID mob = game.mobs().iterator().next();
        game.onMobDied(mob, Optional.empty(), false, false, BlockPos.ZERO);
        game.onMobHit(UUID.randomUUID(), alice);
        game.onMobDied(UUID.randomUUID(), Optional.of(alice), false, false, BlockPos.ZERO);
        assertEquals(start, game.player(alice).orElseThrow().points());
        assertFalse(game.hasMob(mob));
    }

    @Test
    void aLostMobIsSpawnedAgain() {
        Game game = startedGame();
        tick(game, world, 50);
        UUID lost = game.mobs().iterator().next();
        game.onMobLost(lost);
        tick(game, world, 5);
        assertEquals(4, world.spawned.size(), "3 for the round plus 1 replacement");
        killAll(game, Optional.of(alice));
        tick(game, world, 1);
        assertInstanceOf(GamePhase.Intermission.class, game.phase());
    }

    @Test
    void lateJoinersSpectateUntilTheNextRound() {
        Game game = startedGame();
        UUID bob = UUID.randomUUID();
        assertTrue(game.join(bob));
        assertFalse(game.player(bob).orElseThrow().alive());
        tick(game, world, 50);
        killAll(game, Optional.of(alice));
        tick(game, world, 5);
        assertTrue(game.player(bob).orElseThrow().alive(), "bob is in for round 2");
        assertTrue(world.respawned.contains(bob));
    }

    @Test
    void everyoneDeadEndsTheGameAndCleansUp() {
        Game game = startedGame();
        tick(game, world, 50);
        Set<UUID> mobs = new HashSet<>(game.mobs());
        game.onPlayerDied(alice, world);
        assertEquals(List.of(alice), world.spectated);
        tick(game, world, 1);
        assertEquals(new GamePhase.GameOver(1, 2), game.phase());
        assertEquals(mobs, new HashSet<>(world.removed));
        assertTrue(seen.contains(new GameEvent.GameEnd(1)));
        assertFalse(game.finished());
        tick(game, world, 3);
        assertTrue(game.finished());
    }

    @Test
    void zombiesSpawnNearThePlayersZone() {
        Game game = startedGame();
        world.positions.put(alice, new BlockPos(32, 0, 5));
        killAll(game, Optional.of(alice));
        world.spawnedAt.clear();
        tick(game, world, 60);
        assertFalse(world.spawnedAt.isEmpty());
        Set<BlockPos> allowed = Set.of(marker(1), marker(2), marker(3));
        assertTrue(allowed.containsAll(world.spawnedAt), "player in C: only B, C, D markers, got " + world.spawnedAt);
    }

    @Test
    void noUsableMarkerMeansNoSpawnButTheRoundKeepsWaiting() {
        Game game = startedGame();
        world.refuseSpawns = true;
        tick(game, world, 50);
        assertTrue(world.spawned.isEmpty());
        assertEquals(3, assertInstanceOf(GamePhase.RoundActive.class, game.phase()).toSpawn());
    }

    @Test
    void betweenRoundsTheStatusShowsTheRoundWithoutACountdown() {
        Game game = startedGame();
        tick(game, world, 50);
        killAll(game, Optional.of(alice));
        tick(game, world, 1);
        assertInstanceOf(GamePhase.Intermission.class, game.phase());
        assertEquals("Round 1", game.status(alice).phase());
    }

    @Test
    void spawnsCarryTheRosterProfile() {
        Game game = startedGame();
        tick(game, world, 10);
        assertFalse(world.specs.isEmpty());
        assertTrue(world.specs.stream().allMatch(spec -> spec.profile().toString().equals("test:zombie")));
    }

    @Test
    void profilesThatDontFitAMarkersWindowAreNotSpawnedThere() {
        ResourceLocation big = ResourceLocation.tryBuild("test", "golem");
        MobRoster roster = new MobRoster(List.of(
                new MobRoster.Entry(ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty()),
                new MobRoster.Entry(big, 1000, 1, Optional.empty())));
        Game game = new Game(SETTINGS, map(), roster, RandomSource.create(7), events);
        world.canSpawn = (profile, marker) -> !profile.equals(big);
        game.join(alice);
        game.start();
        tick(game, world, 60);
        assertFalse(world.specs.isEmpty());
        assertTrue(world.specs.stream().noneMatch(spec -> spec.profile().equals(big)), "the golem never fits, despite its weight");
    }

    @Test
    void chargingTakesPointsOnlyFromAlivePlayersWhoCanAffordIt() {
        Game game = newGame(SETTINGS);
        game.join(alice);
        int start = game.player(alice).orElseThrow().points();
        assertEquals(Optional.of(new Refusal.NotEnoughPoints(start + 1, start)), game.charge(alice, start + 1, false));
        assertEquals(start, game.player(alice).orElseThrow().points(), "a refusal charges nothing");
        assertEquals(Optional.empty(), game.charge(alice, 100, false));
        assertEquals(start - 100, game.player(alice).orElseThrow().points());
        game.refund(alice, 100);
        assertEquals(start, game.player(alice).orElseThrow().points());
        assertEquals(Optional.of(new Refusal.NotPlaying()), game.charge(UUID.randomUUID(), 0, false), "strangers can't buy");
    }

    @Test
    void perksAreRecordedWithAnEventAndTakenTogether() {
        Game game = newGame(SETTINGS);
        game.join(alice);
        ResourceLocation jug = ResourceLocation.tryBuild("packapunch", "juggernog");
        ResourceLocation tap = ResourceLocation.tryBuild("packapunch", "double_tap");
        PerkDef def = new PerkDef("perk.test", 100, true, 0, List.of());
        game.givePerk(alice, jug, def);
        game.givePerk(alice, tap, def);
        game.givePerk(UUID.randomUUID(), tap, def);
        assertEquals(List.of(jug, tap), List.copyOf(game.player(alice).orElseThrow().perks().keySet()), "in the order bought");
        assertEquals(List.of(new GameEvent.PerkBought(alice, jug), new GameEvent.PerkBought(alice, tap)),
                seen.stream().filter(e -> e instanceof GameEvent.PerkBought).toList(), "strangers get nothing and fire nothing");
        assertEquals(List.of(jug, tap), List.copyOf(game.takePerks(alice).keySet()));
        assertTrue(game.player(alice).orElseThrow().perks().isEmpty());
        assertTrue(game.takePerks(alice).isEmpty());
    }

    @Test
    void aMapWithAPowerSwitchStartsUnpoweredAndPowerComesOnOnce() {
        GameMap powered = map();
        assertTrue(new Game(SETTINGS, powered, ROSTER, RandomSource.create(7), events).powerOn(), "no switch: powered from the start");
        GameMap withSwitch = GameMap.of(powered.map().withFeature(new BlockPos(3, 0, 3), FeatureKind.POWER_SWITCH), Set.of());
        Game game = new Game(SETTINGS, withSwitch, ROSTER, RandomSource.create(7), events);
        game.join(alice);
        assertFalse(game.powerOn());
        assertEquals(Optional.of(new Refusal.NeedsPower()), game.charge(alice, 0, true));
        assertTrue(game.turnPowerOn(Optional.of(alice)));
        assertFalse(game.turnPowerOn(Optional.empty()), "already on");
        assertEquals(Optional.empty(), game.charge(alice, 0, true));
        assertEquals(1, seen.stream().filter(e -> e instanceof GameEvent.PowerOn).count(), "power_on fires once");
    }

    @Test
    void openingADoorUnlocksTheZonesBeyondItOnce() {
        ZombiesMap map = ZombiesMap.EMPTY
                .withZone(new Zone(ZONES.get(0), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withZone(new Zone(ZONES.get(1), List.of(new BlockBox(11, 0, 0, 19, 3, 9)), false))
                .withZone(new Zone(ZONES.get(2), List.of(new BlockBox(20, 0, 0, 29, 3, 9)), false));
        Game game = new Game(SETTINGS, GameMap.of(map, Set.of()), ROSTER, RandomSource.create(7), events);
        assertEquals(Set.of(ZONES.get(0)), game.unlocked(), "b sits behind the door, and c is only reachable through b");
        Set<ZoneId> added = game.openDoor("gate", Optional.of(alice),
                dev.candycanecazoo.packapunch.map.ZoneGraph.of(map, Set.of(dev.candycanecazoo.packapunch.map.Passages.Link.of(ZONES.get(0), ZONES.get(1)))));
        assertEquals(Set.of(ZONES.get(1), ZONES.get(2)), added, "c touches b, so it opens up too");
        assertEquals(Set.of(ZONES.get(0), ZONES.get(1), ZONES.get(2)), game.unlocked());
        assertTrue(game.doorOpen("gate"));
        assertEquals(Set.of(), game.openDoor("gate", Optional.empty(), dev.candycanecazoo.packapunch.map.ZoneGraph.of(map, Set.of())));
        assertEquals(List.of(new GameEvent.DoorOpened("gate", Optional.of(alice), List.of(ZONES.get(1), ZONES.get(2)))),
                seen.stream().filter(e -> e instanceof GameEvent.DoorOpened).toList(), "door_opened fires once, listing what it unlocked");
    }
}
