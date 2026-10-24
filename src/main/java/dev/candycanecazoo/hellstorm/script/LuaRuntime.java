package dev.candycanecazoo.hellstorm.script;

import dev.candycanecazoo.hellstorm.game.GameEvent;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One game's sandboxed Lua state. Built from the datapack scripts at game start and thrown away at game end.
 *
 * <p>The sandbox protects the host machine: no io, os, luajava, debug, coroutines, package/Java loading, dofile,
 * loadfile, load, or binary chunks. String methods resolve in the runtime's own string table ({@link SharedStrings}), never
 * in another mod's. It does not protect the world: {@code command} runs at the configured permission level, the same trust
 * a datapack function gets. Every top-level run and listener call has an instruction budget, and strings a script builds
 * are capped at the max string length.
 */
public final class LuaRuntime {
    public record Limits(long instructionBudget, int maxConsecutiveErrors, int maxStringLength) {}

    private enum Outcome { RAN, FAILED, OUT_OF_MEMORY }

    private static final class Listener {
        final String event;
        final LuaFunction function;
        final String source;
        final EventDef.Filter filter;
        final int priority;
        int consecutiveErrors;
        boolean disabled;

        Listener(String event, LuaFunction function, String source, EventDef.Filter filter, int priority) {
            this.event = event;
            this.function = function;
            this.source = source;
            this.filter = filter;
            this.priority = priority;
        }
    }

    private final Map<String, String> scripts;
    private final ScriptHost host;
    private final Limits limits;
    private final Globals globals = new Globals();
    private final InstructionBudget budget;
    private LuaTable strings;
    private final Map<String, LuaValue> loaded = new HashMap<>();
    private final Deque<String> loading = new ArrayDeque<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<String> loadErrors = new ArrayList<>();
    private String running = "script";
    /** How many guarded calls are in progress: a script's command can fire an event, which runs listeners inside it. */
    private int depth;

    private LuaRuntime(Map<String, String> scripts, ScriptHost host, Limits limits) {
        this.scripts = Map.copyOf(scripts);
        this.host = host;
        this.limits = limits;
        this.budget = new InstructionBudget(limits.maxStringLength());
    }

    /**
     * Builds the runtime: runs each namespace's {@code main} script and every script an EventDef names, then binds the
     * EventDefs. Problems are collected in {@link #loadErrors()} rather than thrown, so one broken script doesn't stop the rest.
     */
    public static LuaRuntime build(Map<String, String> scripts, List<EventDef> defs, ScriptHost host, Limits limits) {
        LuaRuntime runtime = new LuaRuntime(scripts, host, limits);
        runtime.installSandbox();
        List<String> entries = new ArrayList<>(scripts.keySet().stream().filter(id -> id.endsWith(":main")).sorted().toList());
        defs.stream().map(EventDef::script).filter(s -> !entries.contains(s)).forEach(entries::add);
        for (String entry : entries) {
            if (!scripts.containsKey(entry)) {
                continue;
            }
            runtime.guarded("loading " + entry, () -> runtime.require(entry));
        }
        for (EventDef def : defs) {
            runtime.bind(def);
        }
        runtime.listeners.sort(Comparator.comparingInt((Listener l) -> l.priority));
        return runtime;
    }

    public List<String> loadErrors() {
        return List.copyOf(loadErrors);
    }

    /** Runs every enabled listener for the event, lowest priority first. */
    public void dispatch(GameEvent event) {
        String name = LuaEvents.name(event);
        int round = LuaEvents.round(event).orElseGet(host::round);
        for (Listener listener : List.copyOf(listeners)) {
            if (listener.disabled || !listener.event.equals(name) || !listener.filter.accepts(round)) {
                continue;
            }
            switch (guarded(listener.source, () -> listener.function.call(LuaEvents.table(event)))) {
                case RAN -> listener.consecutiveErrors = 0;
                case FAILED -> {
                    if (++listener.consecutiveErrors >= limits.maxConsecutiveErrors()) {
                        disable(listener, "disabled after " + listener.consecutiveErrors + " errors in a row");
                    }
                }
                case OUT_OF_MEMORY -> disable(listener, "disabled: it ran the server out of memory");
            }
        }
    }

    private void disable(Listener listener, String why) {
        listener.disabled = true;
        host.log(ScriptHost.LogLevel.ERROR, listener.source, why);
    }

    private void bind(EventDef def) {
        String source = "event def " + def.script() + "#" + def.function();
        if (!LuaEvents.NAMES.contains(def.event())) {
            loadError(source, "unknown event '" + def.event() + "'");
            return;
        }
        if (!scripts.containsKey(def.script())) {
            loadError(source, "no script '" + def.script() + "' (expected data/<ns>/hellstorm/scripts/<path>.lua)");
            return;
        }
        LuaValue module = loaded.getOrDefault(def.script(), LuaValue.NIL);
        LuaValue[] found = {LuaValue.NIL};
        // Guarded: a module's or _G's __index is script code too.
        guarded(source, () -> {
            LuaValue inModule = module.istable() ? module.get(def.function()) : LuaValue.NIL;
            found[0] = inModule.isfunction() ? inModule : globals.get(def.function());
        });
        LuaValue fn = found[0];
        if (!fn.isfunction()) {
            loadError(source, "script '" + def.script() + "' defines no function '" + def.function() + "'");
            return;
        }
        listeners.add(new Listener(def.event(), (LuaFunction) fn, source, def.filter(), def.priority()));
    }

    private void loadError(String source, String message) {
        loadErrors.add(source + ": " + message);
        host.log(ScriptHost.LogLevel.ERROR, source, message);
    }

    /**
     * Runs Lua code with this runtime's string methods, logging any failure. The outermost call gets a fresh budget; a call
     * nested inside it (a listener run by an event its command fired) spends what is left of the same one.
     */
    private Outcome guarded(String source, Runnable body) {
        if (depth == 0) {
            budget.reset(limits.instructionBudget());
        }
        depth++;
        String outer = running;
        running = source;
        LuaTable outerStrings = SharedStrings.enter(strings);
        try {
            body.run();
            return Outcome.RAN;
        } catch (LuaError e) {
            report(source, e.getMessage());
        } catch (InstructionBudget.Exceeded e) {
            report(source, e.getMessage() + " (an endless loop?)");
        } catch (StackOverflowError e) {
            report(source, "stack overflow (runaway recursion?)");
        } catch (OutOfMemoryError e) {
            report(source, "out of memory");
            return Outcome.OUT_OF_MEMORY;
        } finally {
            SharedStrings.exit(outerStrings);
            running = outer;
            depth--;
        }
        return Outcome.FAILED;
    }

    private void report(String source, String message) {
        if (source.startsWith("loading ")) {
            loadErrors.add(source + ": " + message);
        }
        host.log(ScriptHost.LogLevel.ERROR, source, message);
    }

    private LuaValue require(String id) {
        LuaValue cached = loaded.get(id);
        if (cached != null) {
            return cached;
        }
        if (loading.contains(id)) {
            List<String> chain = new ArrayList<>(loading);
            java.util.Collections.reverse(chain);
            chain.add(id);
            throw new LuaError("require cycle: " + String.join(" -> ", chain));
        }
        String text = scripts.get(id);
        if (text == null) {
            throw new LuaError("no script '" + id + "' (expected data/<ns>/hellstorm/scripts/<path>.lua)");
        }
        loading.push(id);
        try {
            LuaValue chunk = globals.load(text, "@" + id, globals);
            LuaValue result = chunk.call();
            LuaValue module = result.isnil() ? LuaValue.TRUE : result;
            loaded.put(id, module);
            return module;
        } finally {
            loading.pop();
        }
    }

    private void installSandbox() {
        SharedStrings.install();
        globals.load(new BaseLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new MathLib());
        globals.load(budget);
        LuaC.install(globals);
        for (String removed : List.of("debug", "dofile", "loadfile", "load", "loadstring", "collectgarbage", "require", "module", "package")) {
            globals.set(removed, LuaValue.NIL);
        }
        strings = globals.get("string").checktable();
        strings.set("dump", LuaValue.NIL);
        LuaValue rep = strings.get("rep");
        strings.set("rep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                long length = (long) args.checkstring(1).length() * Math.max(0, args.optint(2, 1));
                if (length > limits.maxStringLength()) {
                    throw tooLong("string.rep", length);
                }
                return rep.invoke(args);
            }
        });
        capResult(strings, "string", "gsub");
        capResult(strings, "string", "format");
        capResult(globals.get("table").checktable(), "table", "concat");
        LuaTable stringMetatable = LuaValue.tableOf(new LuaValue[] {LuaValue.INDEX, strings});
        LuaValue getmetatable = globals.get("getmetatable");
        globals.set("getmetatable", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                return value.type() == LuaValue.TSTRING ? stringMetatable : getmetatable.call(value);
            }
        });
        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    line.append(i > 1 ? "\t" : "").append(args.arg(i).tojstring());
                }
                host.log(ScriptHost.LogLevel.INFO, currentSource(), line.toString());
                return LuaValue.NONE;
            }
        });
        globals.set("require", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                return LuaRuntime.this.require(name.checkjstring());
            }
        });
        globals.set("command", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String command = args.checkjstring(1);
                // The command may run another mod's Lua on this thread, which must get its own string methods.
                LuaTable outerStrings = SharedStrings.enter(null);
                ScriptHost.CommandResult result;
                try {
                    result = host.runCommand(command);
                } finally {
                    SharedStrings.exit(outerStrings);
                }
                return LuaValue.varargsOf(LuaValue.valueOf(result.success()), LuaValue.valueOf(result.result()));
            }
        });
        LuaTable events = new LuaTable();
        events.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue fn) {
                String event = name.checkjstring();
                if (!LuaEvents.NAMES.contains(event)) {
                    throw new LuaError("unknown event '" + event + "'. Known events: " + String.join(", ", sorted(LuaEvents.NAMES)));
                }
                listeners.add(new Listener(event, fn.checkfunction(), "events.on('" + event + "') in " + currentSource(), EventDef.Filter.NONE, 0));
                return LuaValue.NIL;
            }
        });
        globals.set("events", events);
        LuaTable log = new LuaTable();
        for (ScriptHost.LogLevel level : ScriptHost.LogLevel.values()) {
            log.set(level.name().toLowerCase(), new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue message) {
                    host.log(level, currentSource(), message.tojstring());
                    return LuaValue.NIL;
                }
            });
        }
        globals.set("log", log);
        LuaTable game = new LuaTable();
        game.set("round", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(host.round());
            }
        });
        game.set("players", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable list = new LuaTable();
                List<ScriptHost.PlayerInfo> players = host.players();
                for (int i = 0; i < players.size(); i++) {
                    ScriptHost.PlayerInfo p = players.get(i);
                    LuaTable entry = new LuaTable();
                    entry.set("uuid", p.id().toString());
                    entry.set("name", p.name());
                    entry.set("points", p.points());
                    entry.set("alive", LuaValue.valueOf(p.alive()));
                    list.set(i + 1, entry);
                }
                return list;
            }
        });
        globals.set("game", game);
    }

    /** Wraps {@code lib[name]} so a string it returns obeys the max string length: it has been built, but goes no further. */
    private void capResult(LuaTable lib, String libName, String name) {
        LuaValue original = lib.get(name);
        lib.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = original.invoke(args);
                if (result.arg1() instanceof LuaString s && s.length() > limits.maxStringLength()) {
                    throw tooLong(libName + "." + name, s.length());
                }
                return result;
            }
        });
    }

    private LuaError tooLong(String function, long length) {
        return new LuaError(function + " result too long (" + length + " > " + limits.maxStringLength() + ")");
    }

    /** The script being loaded, or else the listener being run, for log lines and errors. */
    private String currentSource() {
        return loading.isEmpty() ? running : loading.peek();
    }

    private static List<String> sorted(Set<String> names) {
        return names.stream().sorted().toList();
    }
}
