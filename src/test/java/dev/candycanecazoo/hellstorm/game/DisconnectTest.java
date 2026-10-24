package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dropping out of a game and coming back: the grace time, what's kept, and the player cap. */
class DisconnectTest {
    private static final int GRACE = 30;
    private static final GameSettings.Players D = GameSettings.DEFAULT.players();
    private static final GameSettings SETTINGS = new GameSettings(
            new GameSettings.Rounds(8, 0, 0.15, 0.004, 100, 100, 0.23, 0.23, 0.32, 24, 1, 1.0, 150, 2, 2, 0.0),
            GameSettings.DEFAULT.spawning(), GameSettings.DEFAULT.economy(),
            new GameSettings.Players(D.startingGuns(), D.startingItems(), D.reserveMagazines(), D.startingGunsUnlimitedAmmo(), D.weaponSlots(),
                    D.perkLimit(), 2, GRACE, D.knockbackReduction(), D.friendlyFire()),
            GameSettings.DEFAULT.windows(), GameSettings.DEFAULT.doors(), GameSettings.DEFAULT.box(), GameSettings.DEFAULT.pap(),
            new GameSettings.Downs(400, 10, 2.5, 3, 5, 4.0),
            GameSettings.DEFAULT.powerUps(), GameSettings.DEFAULT.traps(), GameSettings.DEFAULT.waveEvents());

    private FakeWorld world;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(2, 0, 2));
        world.positions.put(bob, new BlockPos(3, 0, 2));
    }

    private Game lobby() {
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("a"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withFeature(new BlockPos(5, 0, 5), FeatureKind.SPAWN);
        Game game = new Game(SETTINGS, GameMap.of(map, Set.of()), new MobRoster(List.of(
                new MobRoster.Entry(ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty()))), RandomSource.create(3), new GameEvents());
        assertTrue(game.join(alice));
        assertTrue(game.join(bob));
        return game;
    }

    private Game playing() {
        Game game = lobby();
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

    @Test
    void comingBackInTimeKeepsTheirPlaceAndPoints() {
        Game game = playing();
        game.refund(bob, 1234);
        int points = game.player(bob).orElseThrow().points();
        game.disconnect(bob);
        tick(game, GRACE - 1);
        assertTrue(game.reconnect(bob), "back within the grace time");
        assertEquals(points, game.player(bob).orElseThrow().points());
        assertInstanceOf(LifeState.Alive.class, game.player(bob).orElseThrow().life());
        assertFalse(game.reconnect(bob), "already back");
    }

    @Test
    void outlastingTheGraceTimeTakesThemOutOfTheGame() {
        Game game = playing();
        game.disconnect(bob);
        tick(game, GRACE);
        assertTrue(game.player(bob).isEmpty());
        assertFalse(game.reconnect(bob));
    }

    @Test
    void someoneAwayStillCountsAsFightingSoTheGameGoesOn() {
        Game game = playing();
        game.disconnect(bob);
        game.onLethalDamage(alice, world);
        tick(game, 1);
        assertInstanceOf(GamePhase.RoundActive.class, game.phase(), "bob is only away");
        tick(game, GRACE);
        assertInstanceOf(GamePhase.GameOver.class, game.phase(), "bob didn't come back and alice is down");
    }

    @Test
    void someoneDownWhoDropsSpectatesInsteadOfBleedingOutUnseenAndIsntRespawnedWhileAway() {
        Game game = playing();
        game.onLethalDamage(bob, world);
        game.disconnect(bob);
        world.respawned.clear();
        assertInstanceOf(LifeState.Spectating.class, game.player(bob).orElseThrow().life());
        for (int i = 0; i < 400 && !(game.phase() instanceof GamePhase.RoundActive active && active.round() == 2); i++) {
            tick(game, 1);
            game.mobs().forEach(mob -> game.onMobDied(mob, Optional.of(alice), false, false, BlockPos.ZERO));
        }
        assertTrue(game.phase() instanceof GamePhase.RoundActive active && active.round() == 2, "round 2 should begin: " + game.phase());
        assertInstanceOf(LifeState.Spectating.class, game.player(bob).orElseThrow().life(), "not respawned while away");
        assertFalse(world.respawned.contains(bob), "the round respawned bob while he was away");
    }

    @Test
    void droppingOutOfTheLobbyJustLeaves() {
        Game game = lobby();
        game.disconnect(bob);
        assertTrue(game.player(bob).isEmpty());
    }

    @Test
    void droppingOutDuringTheCountdownJustLeavesToo() {
        Game game = lobby();
        assertTrue(game.start());
        game.disconnect(bob);
        assertTrue(game.player(bob).isEmpty(), "the start can't put someone offline in play");
    }

    @Test
    void aFullGameTakesNoMore() {
        Game game = lobby();
        assertTrue(game.full());
        assertFalse(game.join(UUID.randomUUID()), "max_players is 2 here");
    }
}
