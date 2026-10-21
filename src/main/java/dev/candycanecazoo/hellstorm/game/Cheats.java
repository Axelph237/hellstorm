package dev.candycanecazoo.hellstorm.game;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Testing aids, toggled per player with {@code /hs debug}. In memory only: a server restart clears them. */
public final class Cheats {
    public enum Cheat {
        /** Purchases cost nothing and never fail for lack of points. */
        INFINITE_MONEY("infinite_money"),
        /** All damage is ignored. */
        GOD("god");

        private final String id;

        Cheat(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private static final java.util.Map<Cheat, Set<UUID>> ON = new java.util.EnumMap<>(Cheat.class);

    static {
        for (Cheat cheat : Cheat.values()) {
            ON.put(cheat, ConcurrentHashMap.newKeySet());
        }
    }

    private Cheats() {}

    public static boolean has(UUID player, Cheat cheat) {
        return ON.get(cheat).contains(player);
    }

    public static void set(UUID player, Cheat cheat, boolean on) {
        if (on) {
            ON.get(cheat).add(player);
        } else {
            ON.get(cheat).remove(player);
        }
    }
}
