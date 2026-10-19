package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.net.MapSyncPacket;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.UnaryOperator;

/** The one way to change a level's map: saves it and pushes it to every player in that level. */
public final class MapEdits {
    private MapEdits() {}

    public static boolean apply(ServerLevel level, UnaryOperator<ZombiesMap> edit) {
        MapSavedData data = MapSavedData.get(level);
        boolean changed = data.update(edit);
        if (changed) {
            PapNetwork.sendToLevel(level, new MapSyncPacket(data.map()));
        }
        return changed;
    }

    public static void syncTo(ServerPlayer player) {
        PapNetwork.send(player, new MapSyncPacket(MapSavedData.get(player.serverLevel()).map()));
    }
}
