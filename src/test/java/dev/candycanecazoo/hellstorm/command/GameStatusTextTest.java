package dev.candycanecazoo.hellstorm.command;

import dev.candycanecazoo.hellstorm.game.GamePhase;
import dev.candycanecazoo.hellstorm.game.LifeState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameStatusTextTest {
    @Test
    void eachPhaseReadsAsASentence() {
        assertEquals("In the lobby: /hs game start when everyone is in", GameCommands.phaseText(new GamePhase.Lobby(), 0));
        assertEquals("Starting in 3 s", GameCommands.phaseText(new GamePhase.Countdown(60), 0));
        assertEquals("Round 3: 12 zombies still to spawn, 4 alive", GameCommands.phaseText(new GamePhase.RoundActive(3, 12, 7), 4));
        assertEquals("Round 1: 1 zombie still to spawn, 0 alive", GameCommands.phaseText(new GamePhase.RoundActive(1, 1, 0), 0));
        assertEquals("Round 4 starts in 10 s", GameCommands.phaseText(new GamePhase.Intermission(4, 199), 0));
        assertEquals("Game over at round 7, ending in 5 s", GameCommands.phaseText(new GamePhase.GameOver(7, 100), 0));
    }

    @Test
    void aDownedPlayerReadsAsDownNotSpectating() {
        assertEquals("down, bleeds out in 30 s", GameCommands.lifeText(new LifeState.Downed(600, Optional.empty(), 0, 0, false)));
        assertEquals("down, being revived", GameCommands.lifeText(new LifeState.Downed(600, Optional.of(UUID.randomUUID()), 5, 60, false)));
        assertEquals("spectating until the next round", GameCommands.lifeText(new LifeState.Spectating()));
        assertEquals("alive", GameCommands.lifeText(new LifeState.Alive()));
        assertEquals("waiting for the start", GameCommands.lifeText(new LifeState.Waiting()));
    }
}
