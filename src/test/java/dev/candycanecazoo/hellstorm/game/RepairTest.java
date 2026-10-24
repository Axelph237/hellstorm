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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rebuilding a window board by holding use: the bar, the board going on as it fills, the pay and its cha-ching. */
class RepairTest {
    private static final int PER_BOARD = 10;
    private static final int PAY = 10;
    private static final GameSettings SETTINGS = new GameSettings(
            GameSettings.DEFAULT.rounds(), GameSettings.DEFAULT.spawning(),
            new GameSettings.Economy(500, 10, 60, 100, 130, PAY, 3 * PAY, 2 * PAY),
            GameSettings.DEFAULT.players(), new GameSettings.Windows(5, 4, 30, PER_BOARD, 6.0),
            GameSettings.DEFAULT.doors(), GameSettings.DEFAULT.box(), GameSettings.DEFAULT.pap(), GameSettings.DEFAULT.downs(),
            GameSettings.DEFAULT.powerUps(), GameSettings.DEFAULT.traps(), GameSettings.DEFAULT.waveEvents());

    private FakeWorld world;
    private UUID alice;
    private Game game;

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        alice = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(1, 0, 1));
        // A zombie to spawn, far enough from alice, so a round can be played out.
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("a"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withFeature(new BlockPos(8, 0, 8), FeatureKind.SPAWN);
        MobRoster roster = new MobRoster(List.of(new MobRoster.Entry(ResourceLocation.tryBuild("test", "zombie"), 1, 1, Optional.empty())));
        game = new Game(SETTINGS, GameMap.of(map, Set.of()), roster, RandomSource.create(1), new GameEvents());
        assertTrue(game.join(alice));
        // Players waiting for the start can't repair.
        game.start();
        while (game.phase() instanceof GamePhase.Countdown) {
            game.tick(world);
        }
    }

    /** Holds use on {@code window} for {@code ticks} ticks, a press every {@code every} ticks. */
    private void hold(String window, int ticks, int every) {
        for (int i = 0; i < ticks; i++) {
            if (i % every == 0) {
                game.holdRepair(alice, window);
            }
            game.tick(world);
        }
    }

    private int points() {
        return game.player(alice).orElseThrow().points();
    }

    @Test
    void aBoardGoesOnWhenTheBarFillsAndTheNextStartsAtOnce() {
        world.boardsMissing.put("w", 3);
        hold("w", PER_BOARD - 1, 1);
        assertEquals(List.of(), world.boardsAdded, "a board went on before its time");
        assertEquals(Optional.of(new GameWorld.Bar("Rebuilding", (PER_BOARD - 1) / (float) PER_BOARD, 0xFFFFFF, 1f / PER_BOARD)), game.status(alice).bar());
        hold("w", 1, 1);
        assertEquals(List.of("w"), world.boardsAdded);
        assertEquals(500 + PAY, points());
        assertEquals(List.of(alice), world.chaChings, "the repairer hears the cha-ching for the points");
        assertEquals(0f, game.status(alice).bar().orElseThrow().progress(), "the next board starts as the last goes on");
        hold("w", PER_BOARD, 1);
        assertEquals(List.of("w", "w"), world.boardsAdded);
    }

    @Test
    void rightClicksRepeatCountsAsHolding() {
        world.boardsMissing.put("w", 3);
        hold("w", PER_BOARD, 4);
        assertEquals(List.of("w"), world.boardsAdded);
    }

    @Test
    void lettingGoOrTurningToAnotherWindowStartsTheBoardOver() {
        world.boardsMissing.put("w", 3);
        world.boardsMissing.put("v", 3);
        hold("w", PER_BOARD / 2, 1);
        for (int i = 0; i < 6; i++) {
            game.tick(world);
        }
        assertEquals(Optional.empty(), game.status(alice).bar(), "the bar stays after letting go");
        hold("w", PER_BOARD - 1, 1);
        hold("v", PER_BOARD - 1, 1);
        assertEquals(List.of(), world.boardsAdded, "a board's time carried over a let go or to another window");
    }

    @Test
    void theLastBoardEndsTheBarAndTheCapEndsThePayAndItsCashSound() {
        world.boardsMissing.put("w", 3);
        hold("w", 3 * PER_BOARD, 1);
        assertEquals(List.of("w", "w", "w"), world.boardsAdded);
        assertEquals(500 + 2 * PAY, points(), "a window's repair cap is two boards' pay");
        assertEquals(List.of(alice, alice), world.chaChings, "no cha-ching for a board that paid nothing");
        assertEquals(Optional.empty(), game.status(alice).bar(), "a full window shows no bar");
        hold("w", PER_BOARD, 1);
        assertEquals(3, world.boardsAdded.size(), "a full window takes no more boards");
    }

    @Test
    void pastAWindowsCapAnotherWindowStillPaysUpToTheRoundsCap() {
        world.boardsMissing.put("w", 3);
        world.boardsMissing.put("v", 3);
        hold("w", 3 * PER_BOARD, 1);
        assertEquals(500 + 2 * PAY, points());
        hold("v", PER_BOARD, 1);
        assertEquals(500 + 3 * PAY, points(), "a different window pays past the first window's cap");
        hold("v", PER_BOARD, 1);
        assertEquals(List.of("w", "w", "w", "v", "v"), world.boardsAdded);
        assertEquals(500 + 3 * PAY, points(), "the round's cap of three boards' pay holds across windows");
    }

    @Test
    void aNewRoundResetsEveryWindowsCap() {
        world.boardsMissing.put("w", 3);
        world.boardsMissing.put("v", 1);
        hold("w", 2 * PER_BOARD, 1);
        hold("v", PER_BOARD, 1);
        assertEquals(500 + 3 * PAY, points(), "both caps reached");
        playOutTheRound();
        world.boardsMissing.put("v", 3);
        hold("w", PER_BOARD, 1);
        hold("v", PER_BOARD, 1);
        assertEquals(500 + 5 * PAY, points(), "a window that hit its cap last round pays again, and so does the round");
    }

    /** Kills every zombie, unpaid, until the next round starts. */
    private void playOutTheRound() {
        int round = assertInstanceOf(GamePhase.RoundActive.class, game.phase()).round();
        for (int i = 0; i < 10_000 && !(game.phase() instanceof GamePhase.RoundActive next && next.round() > round); i++) {
            for (UUID mob : List.copyOf(game.mobs())) {
                game.onMobDied(mob, Optional.empty(), false, false, BlockPos.ZERO);
            }
            game.tick(world);
        }
        assertEquals(round + 1, assertInstanceOf(GamePhase.RoundActive.class, game.phase()).round());
    }
}
