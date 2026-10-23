package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameEvent;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Each game event plays its cue to the players in the game ({@code game/GameSounds}). */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameSoundsGameTests {
    private GameSoundsGameTests() {}

    private static void heard(GameTestHelper helper, ListeningPlayer player, GameEvent event, GameManager.Active active, List<SoundEvent> expected) {
        player.heard.clear();
        active.game().events().emit(event);
        helper.assertTrue(player.heard.equals(expected), event + " should play " + expected + ", played " + player.heard);
    }

    @GameTest(template = "floor", batch = "game_sounds")
    public static void eachGameEventPlaysItsCueToThePlayersInTheGame(GameTestHelper helper) {
        ListeningPlayer player = new ListeningPlayer(helper.getLevel(), "pap_sounds_player");
        ListeningPlayer outsider = new ListeningPlayer(helper.getLevel(), "pap_sounds_outsider");
        GameManager.Active active = PurchaseGameTests.game(helper, List.<ServerPlayer>of(player));
        try {
            heard(helper, player, new GameEvent.GameStart(List.of(player.getUUID())), active, List.of(PapRegistries.GAME_START.get()));
            heard(helper, player, new GameEvent.RoundStart(1), active, List.of());
            heard(helper, player, new GameEvent.RoundStart(2), active, List.of(PapRegistries.ROUND_START.get()));
            heard(helper, player, new GameEvent.RoundEnd(2), active, List.of(PapRegistries.ROUND_END.get()));
            heard(helper, player, new GameEvent.PowerOn(Optional.empty()), active, List.of());
            heard(helper, player, new GameEvent.PowerUpCollected(Hellstorm.id("nuke"), player.getUUID()), active,
                    List.of(PapRegistries.POWER_UP_DRAGON.get(), PapRegistries.POWER_UP_CELEBRATE.get()));
            heard(helper, player, new GameEvent.PlayerRevived(player.getUUID(), Optional.empty()), active, List.of(PapRegistries.REVIVED.get()));
            heard(helper, player, new GameEvent.PlayerBledOut(player.getUUID()), active, List.of(PapRegistries.BLED_OUT.get()));
            helper.assertTrue(outsider.heard.isEmpty(), "a player outside the game heard " + outsider.heard);
        } finally {
            GameManager.end(active);
        }
        helper.succeed();
    }
}
