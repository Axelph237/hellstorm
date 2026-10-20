package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import dev.candycanecazoo.packapunch.perk.PerkEffect;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Going down, being revived, bleeding out, solo Quick Revive, and regen, against a fake world. */
class DownsTest {
    private static final int BLEED = 40;
    private static final int REVIVE = 10;
    private static final int REGEN_DELAY = 5;
    private static final GameSettings SETTINGS = new GameSettings(
            new GameSettings.Rounds(2, 0, 0, 0.15, 100, 0.23, 0.32, 0.004, 0.0001, 24, 1, 3, 2, 2),
            GameSettings.DEFAULT.spawning(), GameSettings.DEFAULT.economy(), GameSettings.DEFAULT.players(), GameSettings.DEFAULT.windows(),
            GameSettings.DEFAULT.doors(), GameSettings.DEFAULT.box(), GameSettings.DEFAULT.pap(),
            new GameSettings.Downs(BLEED, REVIVE, 2.5, 3, REGEN_DELAY, 4.0),
            GameSettings.DEFAULT.powerUps(), GameSettings.DEFAULT.traps(), GameSettings.DEFAULT.waveEvents());
    private static final ResourceLocation QUICK_REVIVE = ResourceLocation.tryBuild("packapunch", "quick_revive");
    private static final PerkDef QUICK_REVIVE_DEF = new PerkDef("perk.packapunch.quick_revive", 1500, false, 0, List.of(new PerkEffect.SelfRevive()));

    private FakeWorld world;
    private List<GameEvent> seen;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        seen = new ArrayList<>();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(2, 0, 2));
        world.positions.put(bob, new BlockPos(3, 0, 2));
    }

    private Game game(UUID... players) {
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("a"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withFeature(new BlockPos(5, 0, 5), FeatureKind.SPAWN);
        GameEvents events = new GameEvents();
        events.subscribe(seen::add);
        Game game = new Game(SETTINGS, GameMap.of(map, Set.of()), new MobRoster(List.of(
                new MobRoster.Entry(ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty()))), RandomSource.create(3), events);
        for (UUID player : players) {
            assertTrue(game.join(player));
        }
        assertTrue(game.start());
        tick(game, 3);
        assertInstanceOf(GamePhase.RoundActive.class, game.phase());
        return game;
    }

    private void tick(Game game, int times) {
        for (int i = 0; i < times; i++) {
            game.tick(world);
        }
    }

    private void holdRevive(Game game, UUID reviver, UUID downed, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.holdRevive(reviver, downed);
            game.tick(world);
        }
    }

    private LifeState life(Game game, UUID player) {
        return game.player(player).orElseThrow().life();
    }

    @Test
    void lethalDamageDownsInsteadOfKillingAndZombiesStopTargeting() {
        Game game = game(alice, bob);
        assertTrue(game.onLethalDamage(alice, world));
        assertInstanceOf(LifeState.Downed.class, life(game, alice));
        assertEquals(List.of(alice), world.downed);
        assertTrue(seen.contains(new GameEvent.PlayerDowned(alice, new BlockPos(2, 0, 2))));
        assertFalse(game.targetable(alice));
        assertTrue(game.charge(alice, 0, false).isPresent(), "downed players can't buy");
        assertTrue(game.onLethalDamage(alice, world), "more damage while down is absorbed");
        assertEquals(1, world.downed.size());
        tick(game, 1);
        assertInstanceOf(GamePhase.RoundActive.class, game.phase(), "bob is still up, so the game goes on");
    }

    @Test
    void aTeammateHoldingUseForTheReviveTimeBringsThemBack() {
        Game game = game(alice, bob);
        game.onLethalDamage(alice, world);
        holdRevive(game, bob, alice, REVIVE - 1);
        assertInstanceOf(LifeState.Downed.class, life(game, alice));
        assertEquals(Optional.of(new GameWorld.Bar("Reviving", (REVIVE - 1) / (float) REVIVE, 0xFFFFFF)), game.status(bob).bar());
        holdRevive(game, bob, alice, 1);
        assertInstanceOf(LifeState.Alive.class, life(game, alice));
        assertEquals(List.of(alice), world.revived);
        assertTrue(seen.contains(new GameEvent.PlayerRevived(alice, Optional.of(bob))));
    }

    @Test
    void reviveSpeedShortensTheRevive() {
        Game game = game(alice, bob);
        world.reviveSpeeds.put(bob, 2.0);
        game.onLethalDamage(alice, world);
        holdRevive(game, bob, alice, REVIVE / 2);
        assertInstanceOf(LifeState.Alive.class, life(game, alice));
    }

    @Test
    void letGoMovingAwayOrTakingDamageResetsTheRevive() {
        Game game = game(alice, bob);
        game.onLethalDamage(alice, world);
        // A hold carries on for a click repeat's worth of ticks (5) after the last press, then lapses.
        holdRevive(game, bob, alice, 3);
        tick(game, 7);
        assertEquals(0, ((LifeState.Downed) life(game, alice)).reviveTicks(), "letting go for longer than a click repeat resets");

        holdRevive(game, bob, alice, 3);
        game.onPlayerHurt(bob);
        tick(game, 1);
        assertEquals(0, ((LifeState.Downed) life(game, alice)).reviveTicks(), "the reviver taking damage resets");

        world.positions.put(bob, new BlockPos(9, 0, 9));
        holdRevive(game, bob, alice, REVIVE + 2);
        assertInstanceOf(LifeState.Downed.class, life(game, alice), "out of range doesn't revive");
    }

    @Test
    void bleedingOutSpectatesUntilTheNextRoundWithAFreshLoadout() {
        Game game = game(alice, bob);
        game.onLethalDamage(alice, world);
        assertEquals("Bleeding out", game.status(alice).bar().orElseThrow().label());
        tick(game, BLEED);
        assertInstanceOf(LifeState.Spectating.class, life(game, alice));
        assertEquals(List.of(alice), world.spectated);
        assertTrue(seen.contains(new GameEvent.PlayerBledOut(alice)));
        long respawnsBefore = world.respawned.stream().filter(alice::equals).count();
        for (UUID mob : game.mobs()) {
            game.onMobDied(mob, Optional.of(bob), false, false, BlockPos.ZERO);
        }
        tick(game, 6);
        assertEquals(2, ((GamePhase.RoundActive) game.phase()).round());
        assertInstanceOf(LifeState.Alive.class, life(game, alice));
        assertEquals(respawnsBefore + 1, world.respawned.stream().filter(alice::equals).count(), "back at the next round");
        assertEquals(List.of(alice), world.restocked, "with a fresh loadout");
    }

    @Test
    void everyoneDownEndsTheGame() {
        Game game = game(alice);
        game.onLethalDamage(alice, world);
        tick(game, 1);
        assertInstanceOf(GamePhase.GameOver.class, game.phase());
    }

    @Test
    void soloQuickReviveSelfRevivesThreeTimesThenTheFourthDownEndsIt() {
        Game game = game(alice);
        for (int i = 0; i < 3; i++) {
            game.givePerk(alice, QUICK_REVIVE, QUICK_REVIVE_DEF);
            assertTrue(game.onLethalDamage(alice, world));
            game.takePerks(alice);
            tick(game, 1);
            assertInstanceOf(GamePhase.RoundActive.class, game.phase(), "self-reviving keeps the game going, down " + (i + 1));
            tick(game, REVIVE);
            assertInstanceOf(LifeState.Alive.class, life(game, alice), "self-revive " + (i + 1));
            assertTrue(seen.contains(new GameEvent.PlayerRevived(alice, Optional.empty())));
        }
        game.givePerk(alice, QUICK_REVIVE, QUICK_REVIVE_DEF);
        game.onLethalDamage(alice, world);
        tick(game, 1);
        assertInstanceOf(GamePhase.GameOver.class, game.phase());
    }

    @Test
    void regenWaitsForTheDelayAfterDamage() {
        Game game = game(alice);
        game.onPlayerHurt(alice);
        tick(game, REGEN_DELAY - 1);
        assertFalse(world.healed.containsKey(alice));
        tick(game, 2);
        assertTrue(world.healed.getOrDefault(alice, 0f) > 0);
    }

    @Test
    void downedPlayersStillEarnPointsForKills() {
        Game game = game(alice, bob);
        tick(game, 1);
        game.onLethalDamage(alice, world);
        int before = game.player(alice).orElseThrow().points();
        UUID mob = game.mobs().iterator().next();
        game.onMobDied(mob, Optional.of(alice), false, false, BlockPos.ZERO);
        assertTrue(game.player(alice).orElseThrow().points() > before);
    }
}
