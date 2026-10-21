package dev.candycanecazoo.hellstorm.feature;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.Supplier;

/** The local player, for prompts that depend on what they carry. Set by the client; empty on a dedicated server. */
public final class Viewer {
    private static volatile Supplier<Player> client = () -> null;

    private Viewer() {}

    public static void set(Supplier<Player> player) {
        client = player;
    }

    public static Optional<Player> get() {
        return Optional.ofNullable(client.get());
    }
}
