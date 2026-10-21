package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.net.NoticePacket;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Short feedback for one player above their hotbar. Use this instead of the action bar: it wraps long lines to fit. */
public final class Notices {
    private Notices() {}

    public static void show(ServerPlayer player, Component message) {
        PapNetwork.send(player, new NoticePacket(message));
    }
}
