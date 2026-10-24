package dev.candycanecazoo.hellstorm.script;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/**
 * LuaJ keeps one string metatable for the whole JVM ({@link LuaString#s_metatable}), so a method call such as
 * {@code ("x"):rep(n)} resolves in whichever string table took the slot first, and TacZ, which bundles the same LuaJ, usually
 * takes it. This puts a router in the slot. While a game's runtime runs Lua on a thread ({@link #enter}), string methods
 * resolve in that runtime's own, capped string table. Everywhere else they resolve in the metatable that was in the slot
 * before, so TacZ's gun scripts keep theirs and scripts can't reach or change it.
 */
final class SharedStrings {
    private static final ThreadLocal<LuaTable> ACTIVE = new ThreadLocal<>();
    private static final LuaTable ROUTER = new LuaTable();
    /** ROUTER's {@code __index}. Empty: every read and write goes through its metatable to the active or previous table. */
    private static final LuaTable PROXY = new LuaTable();
    private static volatile LuaValue previous;

    static {
        LuaTable proxyMeta = new LuaTable();
        proxyMeta.set(LuaValue.INDEX, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                LuaTable active = ACTIVE.get();
                if (active != null) {
                    return active.get(key);
                }
                LuaValue index = previous.get(LuaValue.INDEX);
                return index.isfunction() ? index.call(self, key) : index.get(key);
            }
        });
        proxyMeta.set(LuaValue.NEWINDEX, new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key, LuaValue value) {
                LuaTable active = ACTIVE.get();
                (active != null ? active : previous.get(LuaValue.INDEX)).set(key, value);
                return NONE;
            }
        });
        PROXY.setmetatable(proxyMeta);
        ROUTER.set(LuaValue.INDEX, PROXY);
    }

    private SharedStrings() {}

    /**
     * Puts the router in the slot, keeping what was there for everyone else. Call before loading a {@link StringLib}, which
     * takes the slot if it's empty. Another mod that takes the slot later is routed to the same way at the next {@link #enter}.
     */
    static synchronized void install() {
        LuaValue current = LuaString.s_metatable;
        ROUTER.rawset(LuaValue.INDEX, PROXY);
        if (current == ROUTER) {
            return;
        }
        LuaString.s_metatable = ROUTER;
        previous = current != null ? current
                : LuaValue.tableOf(new LuaValue[] {LuaValue.INDEX, new StringLib().call(LuaValue.EMPTYSTRING, new LuaTable())});
    }

    /** A runtime is about to run Lua on this thread with {@code strings} as its string table, or null for none. Returns what {@link #exit} restores. */
    static LuaTable enter(LuaTable strings) {
        if (LuaString.s_metatable != ROUTER || ROUTER.rawget(LuaValue.INDEX) != PROXY) {
            install();
        }
        LuaTable before = ACTIVE.get();
        ACTIVE.set(strings);
        return before;
    }

    static void exit(LuaTable before) {
        ACTIVE.set(before);
    }
}
