package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
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
            new GameSettings.Rounds(12, 0, 0.15, 0.004, 100, 100, 0.23, 0.23, 0.32, 24, 1, 1.0, 150, 2, 2, 0.0),
            new GameSettings.Spawning(24.0, 0.0),
            GameSettings.DEFAULT.economy(),
            GameSettings.DEFAULT.players(),
            GameSettings.DEFAULT.windows(),
            GameSettings.DEFAULT.doors(),
            GameSettings.DEFAULT.box(),
            GameSettings.DEFAULT.pap(),
            GameSettings.DEFAULT.downs(),
            GameSettings.DEFAULT.powerUps(),
            GameSettings.DEFAULT.traps(),
            GameSettings.DEFAULT.waveEvents());

    private static final MobRoster ROSTER = new MobRoster(List.of(
            new MobRoster.Entry(net.minecraft.resources.ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty())));

    private static final ItemSpec.Plain SKULL = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "skeleton_skull"), 1, Optional.empty());
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
        return GameMap.of(map, offlinePassages(map));
    }

    /** Where the zones' boxes touch, reading every cell inside a zone as open. */
    static Set<dev.candycanecazoo.hellstorm.map.Passages.Link> offlinePassages(ZombiesMap map) {
        var index = dev.candycanecazoo.hellstorm.map.ZoneIndex.of(map);
        return dev.candycanecazoo.hellstorm.map.Passages.find(map, index, dev.candycanecazoo.hellstorm.map.CellProbe.zonesOnly(index), 2).open();
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
        assertEquals(List.of(alice), world.entered);
        assertEquals(List.of(alice), world.respawned);
        assertEquals(List.of(new GameEvent.GameStart(List.of(alice)), new GameEvent.RoundStart(1)), seen);
    }

    @Test
    void joiningBeforeTheStartWaitsUntilTheCountdownEnds() {
        Game game = newGame(SETTINGS);
        game.join(alice);
        PlayerState waiting = game.player(alice).orElseThrow();
        assertInstanceOf(LifeState.Waiting.class, waiting.life());
        assertFalse(waiting.alive() || waiting.inPlay(), "waiting isn't playing");
        assertFalse(game.targetable(alice), "nothing targets a waiting player");
        assertEquals(Optional.of(new Refusal.NotStarted()), game.charge(alice, 0, false));
        game.onRepair(alice, "w");
        assertEquals(SETTINGS.economy().startingPoints(), waiting.points(), "a waiting player earns nothing");
        assertFalse(game.collectPowerUp(ResourceLocation.tryBuild("hellstorm", "max_ammo"), alice));

        game.start();
        UUID bob = UUID.randomUUID();
        assertTrue(game.join(bob));
        assertInstanceOf(LifeState.Waiting.class, game.player(bob).orElseThrow().life(), "joining during the countdown waits too");
        tick(game, world, 2);
        assertEquals(List.of(), world.entered, "the game takes nobody over before the countdown ends");
        tick(game, world, 1);
        assertInstanceOf(GamePhase.RoundActive.class, game.phase());
        for (UUID id : List.of(alice, bob)) {
            assertInstanceOf(LifeState.Alive.class, game.player(id).orElseThrow().life());
            assertTrue(game.targetable(id));
        }
        assertEquals(List.of(alice, bob), world.entered, "both get their state saved and the starting loadout");
        assertEquals(List.of(alice, bob), world.respawned);
        assertEquals(Optional.empty(), game.charge(alice, 0, false));
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
        assertEquals(3 + RoundPlan.zombieCount(SETTINGS.rounds(), 2, 1), world.spawned.size(), "round 2 spawns its own count");
    }

    @Test
    void spawnsRespectTheMaxAliveCapAndTheDelay() {
        GameSettings capped = new GameSettings(
                new GameSettings.Rounds(40, 0, 0.15, 0.004, 100, 100, 0.23, 0.23, 0.32, 2, 5, 1.0, 150, 0, 2, 0.0),
                SETTINGS.spawning(), SETTINGS.economy(), SETTINGS.players(), SETTINGS.windows(), SETTINGS.doors(), SETTINGS.box(), SETTINGS.pap(),
                SETTINGS.downs(), SETTINGS.powerUps(), SETTINGS.traps(), SETTINGS.waveEvents());
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
        assertInstanceOf(LifeState.Spectating.class, game.player(bob).orElseThrow().life(), "the game has started, so no waiting");
        tick(game, world, 50);
        killAll(game, Optional.of(alice));
        tick(game, world, 5);
        assertTrue(game.player(bob).orElseThrow().alive(), "bob is in for round 2");
        assertTrue(world.respawned.contains(bob));
    }

    @Test
    void timedEffectsCountDownShowOnTheHudAndEndOnce() {
        Game game = startedGame();
        net.minecraft.resources.ResourceLocation instaKill = net.minecraft.resources.ResourceLocation.tryBuild("hellstorm", "insta_kill");
        game.startTimed(instaKill, "powerup.hellstorm.insta_kill", SKULL, 5);
        assertTrue(game.timedActive(instaKill));
        tick(game, world, 2);
        assertEquals(List.of(new GameWorld.Timer("powerup.hellstorm.insta_kill", SKULL, 3, 5)), game.status(alice).timers());
        game.startTimed(instaKill, "powerup.hellstorm.insta_kill", SKULL, 5);
        tick(game, world, 4);
        assertTrue(game.timedActive(instaKill), "picking it up again restarts the timer");
        tick(game, world, 1);
        assertFalse(game.timedActive(instaKill));
        assertEquals(List.of(instaKill), world.timedEnded);
        assertEquals(List.of(), game.status(alice).timers());
    }

    @Test
    void gameOverEndsRunningTimedEffects() {
        Game game = startedGame();
        net.minecraft.resources.ResourceLocation fireSale = net.minecraft.resources.ResourceLocation.tryBuild("hellstorm", "fire_sale");
        game.startTimed(fireSale, "powerup.hellstorm.fire_sale", SKULL, 600);
        game.stop(world);
        assertFalse(game.timedActive(fireSale));
        assertEquals(List.of(fireSale), world.timedEnded);
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

    /** Clears the zombies and ticks through rounds until {@code count} more have spawned, returning their markers. */
    private Set<BlockPos> spawnMarkers(Game game, int count) {
        world.spawnedAt.clear();
        for (int i = 0; i < 2000 && world.spawnedAt.size() < count; i++) {
            killAll(game, Optional.of(alice));
            game.tick(world);
        }
        assertTrue(world.spawnedAt.size() >= count, "only " + world.spawnedAt.size() + " spawns");
        return Set.copyOf(world.spawnedAt);
    }

    @Test
    void onlyTheStartZoneIsDiscoveredAtFirstSoOnlyItsMarkerSpawns() {
        Game game = startedGame();
        assertEquals(Set.of(ZONES.get(0)), game.discovered());
        assertEquals(Set.of(marker(0)), spawnMarkers(game, 30), "b, c and d touch a but nobody has been in them");
    }

    @Test
    void aZoneAPlayerStandsInStaysDiscoveredAfterTheyLeave() {
        Game game = startedGame();
        world.positions.put(alice, new BlockPos(25, 0, 2));
        tick(game, world, 1);
        world.positions.put(alice, new BlockPos(2, 0, 2));
        tick(game, world, 1);
        assertEquals(Set.of(ZONES.get(0), ZONES.get(2)), game.discovered(), "alice went from a straight to c, so b and d are still undiscovered");
        assertEquals(Set.of(marker(0), marker(2)), spawnMarkers(game, 60));
    }

    /** Standing on a carpet or slab puts the feet in that block, just under a zone drawn over the floor. */
    @Test
    void aPlayerStandingOnALowFloorUnderAZoneDiscoversIt() {
        Game game = startedGame();
        world.positions.put(alice, new BlockPos(25, -1, 2));
        tick(game, world, 1);
        assertEquals(Set.of(ZONES.get(0), ZONES.get(2)), game.discovered());
    }

    @Test
    void aSpectatorDiscoversNothing() {
        Game game = startedGame();
        UUID bob = UUID.randomUUID();
        world.positions.put(bob, new BlockPos(35, 0, 5));
        assertTrue(game.join(bob));
        assertInstanceOf(LifeState.Spectating.class, game.player(bob).orElseThrow().life());
        tick(game, world, 5);
        assertEquals(Set.of(ZONES.get(0)), game.discovered());
    }

    @Test
    void withARunnerChanceOfOneEveryZombieRuns() {
        GameSettings.Rounds r = SETTINGS.rounds();
        GameSettings allRun = new GameSettings(
                new GameSettings.Rounds(r.baseCount(), r.aiPerPlayer(), r.healthScale(), r.healthGrowth(), r.runFromRound(), r.sprintFromRound(), r.walkSpeed(),
                        r.runSpeed(), r.sprintSpeed(), r.maxAlive(), r.spawnDelayTicks(), r.spawnDelayFactor(), r.intermissionMs(), r.countdownTicks(), r.gameOverTicks(), 1.0),
                SETTINGS.spawning(), SETTINGS.economy(), SETTINGS.players(), SETTINGS.windows(), SETTINGS.doors(), SETTINGS.box(), SETTINGS.pap(),
                SETTINGS.downs(), SETTINGS.powerUps(), SETTINGS.traps(), SETTINGS.waveEvents());
        Game game = newGame(allRun);
        game.join(alice);
        game.start();
        tick(game, world, 50);
        assertEquals(3, world.spawned.size());
        assertTrue(world.runners.containsAll(world.spawned), "every spawn runs, got " + world.runners);
    }

    @Test
    void theLastZombieOfARoundRunsOnce() {
        Game game = startedGame();
        tick(game, world, 50);
        List<UUID> alive = List.copyOf(game.mobs());
        assertEquals(3, alive.size());
        game.onMobDied(alive.get(0), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, world, 5);
        assertTrue(world.runners.isEmpty(), "with a runner chance of 0, two zombies left walk");
        game.onMobDied(alive.get(1), Optional.of(alice), false, false, BlockPos.ZERO);
        tick(game, world, 5);
        assertEquals(List.of(alive.get(2)), world.runners, "the last one runs, once");
    }

    @Test
    void theLastZombieDoesntRunWhileMoreAreStillToSpawn() {
        GameSettings.Rounds r = SETTINGS.rounds();
        GameSettings slow = new GameSettings(
                new GameSettings.Rounds(r.baseCount(), r.aiPerPlayer(), r.healthScale(), r.healthGrowth(), r.runFromRound(), r.sprintFromRound(), r.walkSpeed(),
                        r.runSpeed(), r.sprintSpeed(), r.maxAlive(), 100, r.spawnDelayFactor(), r.intermissionMs(), r.countdownTicks(), r.gameOverTicks(), 0.0),
                SETTINGS.spawning(), SETTINGS.economy(), SETTINGS.players(), SETTINGS.windows(), SETTINGS.doors(), SETTINGS.box(), SETTINGS.pap(),
                SETTINGS.downs(), SETTINGS.powerUps(), SETTINGS.traps(), SETTINGS.waveEvents());
        Game game = newGame(slow);
        game.join(alice);
        game.start();
        tick(game, world, 10);
        assertEquals(1, game.mobs().size(), "one zombie in, two still to come");
        assertTrue(world.runners.isEmpty(), "a lone zombie with more to come walks");
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
        Game game = startedGame();
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
    void aMapWithAPowerSwitchStartsUnpoweredAndPowerComesOnOnce() {
        GameMap powered = map();
        assertTrue(new Game(SETTINGS, powered, ROSTER, RandomSource.create(7), events).powerOn(), "no switch: powered from the start");
        GameMap withSwitch = GameMap.of(powered.map().withFeature(new BlockPos(3, 0, 3), FeatureKind.POWER_SWITCH), Set.of());
        Game game = new Game(SETTINGS, withSwitch, ROSTER, RandomSource.create(7), events);
        game.join(alice);
        game.start();
        tick(game, world, 3);
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
        Game game = new Game(SETTINGS, GameMap.of(map, offlinePassages(map)), ROSTER, RandomSource.create(7), events);
        assertEquals(Set.of(ZONES.get(0)), game.unlocked(), "b sits behind the door, and c is only reachable through b");
        Set<dev.candycanecazoo.hellstorm.map.Passages.Link> opened = new java.util.HashSet<>(offlinePassages(map));
        opened.add(dev.candycanecazoo.hellstorm.map.Passages.Link.of(ZONES.get(0), ZONES.get(1)));
        Set<ZoneId> added = game.openDoor("gate", Optional.of(alice), dev.candycanecazoo.hellstorm.map.ZoneGraph.of(map, opened));
        assertEquals(Set.of(ZONES.get(1), ZONES.get(2)), added, "c touches b, so it opens up too");
        assertEquals(Set.of(ZONES.get(0), ZONES.get(1), ZONES.get(2)), game.unlocked());
        assertEquals(Set.of(ZONES.get(0)), game.discovered(), "opening a door discovers nothing: a player has to walk in");
        assertTrue(game.doorOpen("gate"));
        assertEquals(Set.of(), game.openDoor("gate", Optional.empty(), dev.candycanecazoo.hellstorm.map.ZoneGraph.of(map, Set.of())));
        assertEquals(List.of(new GameEvent.DoorOpened("gate", Optional.of(alice), List.of(ZONES.get(1), ZONES.get(2)))),
                seen.stream().filter(e -> e instanceof GameEvent.DoorOpened).toList(), "door_opened fires once, listing what it unlocked");
    }

    @Test
    void infiniteMoneyBuysAnythingWithoutSpending() {
        Game game = startedGame();
        int start = game.player(alice).orElseThrow().points();
        Cheats.set(alice, Cheats.Cheat.INFINITE_MONEY, true);
        try {
            assertEquals(Optional.empty(), game.charge(alice, start * 100, false));
            game.refund(alice, start * 100);
            assertEquals(start, game.player(alice).orElseThrow().points(), "nothing spent, nothing refunded");
            assertEquals(Optional.of(new Refusal.NotPlaying()), game.charge(UUID.randomUUID(), 1, false));
        } finally {
            Cheats.set(alice, Cheats.Cheat.INFINITE_MONEY, false);
        }
        assertEquals(Optional.of(new Refusal.NotEnoughPoints(start * 100, start)), game.charge(alice, start * 100, false));
    }
}
