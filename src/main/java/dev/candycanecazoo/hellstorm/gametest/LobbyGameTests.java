package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.MapMode;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapModes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Starting a game rebuilds it from the map as it is then, so edits made while the lobby was open are in it. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LobbyGameTests {
    private static final ZoneId START = new ZoneId("gametest_lobby_start");
    private static final ZoneId ADDED = new ZoneId("gametest_lobby_added");

    private LobbyGameTests() {}

    private static Zone room(GameTestHelper helper, ZoneId id, int minZ, int maxZ, boolean start) {
        return new Zone(id, List.of(BlockBox.of(helper.absolutePos(new BlockPos(0, ROOM_Y, minZ)), helper.absolutePos(new BlockPos(8, ROOM_Y + 2, maxZ)))), start);
    }

    /** Opens a lobby on a one-room map with one waiting player, runs {@code check}, and puts the level's map and mode back. */
    private static void inALobby(GameTestHelper helper, String name, java.util.function.BiConsumer<GameManager.Active, ServerPlayer> check) {
        var level = helper.getLevel();
        ZombiesMap saved = MapSavedData.get(level).map();
        MapMode mode = MapSavedData.get(level).mode();
        try {
            MapModes.set(level, MapMode.PLAY);
            MapEdits.apply(level, m -> ZombiesMap.EMPTY.withZone(room(helper, START, 0, 4, true)));
            GameManager.Active lobby = ((GameManager.CreateResult.Created) GameManager.create(level)).active();
            ServerPlayer player = PurchaseGameTests.player(helper, name);
            lobby.game().join(player.getUUID());
            check.accept(lobby, player);
        } finally {
            GameManager.get(level).ifPresent(GameManager::end);
            MapModes.set(level, mode);
            MapEdits.apply(level, m -> saved);
        }
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "lobby_rebuild")
    public static void aRoomAddedWhileTheLobbyIsOpenIsInTheGame(GameTestHelper helper) {
        inALobby(helper, "pap_lobby_added", (lobby, player) -> {
            MapEdits.apply(helper.getLevel(), m -> m.withZone(room(helper, ADDED, 5, 8, false)));
            GameManager.CreateResult result = GameManager.reopen(lobby);
            helper.assertTrue(result instanceof GameManager.CreateResult.Created, "the edited map should open, got " + result);
            GameManager.Active game = ((GameManager.CreateResult.Created) result).active();
            helper.assertTrue(game != lobby && GameManager.get(helper.getLevel()).orElseThrow() == game, "a fresh game replaces the lobby");
            helper.assertTrue(game.game().map().map().zones().containsKey(ADDED), "the room added in the lobby is in the game");
            helper.assertTrue(game.game().player(player.getUUID()).isPresent(), "the waiting player is in the fresh game");
        });
    }

    @GameTest(template = "floor", batch = "lobby_broken")
    public static void aMapBrokenWhileTheLobbyIsOpenRefusesToStartAndKeepsTheLobby(GameTestHelper helper) {
        inALobby(helper, "pap_lobby_broken", (lobby, player) -> {
            MapEdits.apply(helper.getLevel(), m -> ZombiesMap.EMPTY.withZone(room(helper, START, 0, 4, false)));
            GameManager.CreateResult result = GameManager.reopen(lobby);
            helper.assertTrue(result instanceof GameManager.CreateResult.Invalid, "a map with no start room should be refused, got " + result);
            helper.assertTrue(GameManager.get(helper.getLevel()).orElseThrow() == lobby, "the lobby stays open");
            helper.assertTrue(lobby.game().player(player.getUUID()).isPresent(), "with its player");
        });
    }
}
