package dev.candycanecazoo.hellstorm.script;

import dev.candycanecazoo.hellstorm.game.GameEvent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LuaRuntimeTest {
    private static final LuaRuntime.Limits LIMITS = new LuaRuntime.Limits(100_000, 3, 10_000);
    private static final GameEvent ROUND_START = new GameEvent.RoundStart(1);

    private RecordingHost host;

    @BeforeEach
    void setUp() {
        host = new RecordingHost();
    }

    private LuaRuntime build(Map<String, String> scripts, List<EventDef> defs) {
        return LuaRuntime.build(scripts, defs, host, LIMITS);
    }

    private LuaRuntime main(String code) {
        return build(Map.of("test:main", code), List.of());
    }

    @Test
    void dangerousGlobalsAreUnreachable() {
        LuaRuntime runtime = main("""
                local names = {"io", "os", "luajava", "debug", "dofile", "loadfile", "load", "loadstring",
                               "collectgarbage", "package", "module", "coroutine"}
                for _, n in ipairs(names) do
                  if _G[n] ~= nil then log.error("reachable: " .. n) end
                end
                if string.dump ~= nil then log.error("reachable: string.dump") end
                log.info("checked")
                """);
        assertEquals(List.of(), runtime.loadErrors());
        assertEquals(0, host.count("reachable:"), host.logs::toString);
        assertEquals(1, host.count("checked"));
    }

    @Test
    void requireOnlyResolvesDatapackScripts() {
        LuaRuntime runtime = main("require('/etc/passwd')");
        assertEquals(1, runtime.loadErrors().size());
        assertTrue(runtime.loadErrors().get(0).contains("no script '/etc/passwd'"), runtime.loadErrors()::toString);
    }

    @Test
    void anEndlessLoopIsStoppedAndLogged() {
        LuaRuntime runtime = main("events.on('round_start', function() while true do end end)");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runtime.dispatch(ROUND_START));
        assertEquals(1, host.count("instruction budget"), host.logs::toString);
    }

    @Test
    void pcallCannotSwallowTheBudget() {
        LuaRuntime runtime = main("""
                events.on('round_start', function()
                  while true do pcall(function() while true do end end) end
                end)
                """);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runtime.dispatch(ROUND_START));
        assertEquals(1, host.count("instruction budget"), host.logs::toString);
    }

    @Test
    void anEndlessLoopAtLoadTimeIsALoadError() {
        LuaRuntime runtime = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> main("while true do end"));
        assertEquals(1, runtime.loadErrors().size());
    }

    @Test
    void aListenerThatKeepsFailingIsDisabled() {
        LuaRuntime runtime = main("events.on('round_start', function() error('boom') end)");
        for (int i = 0; i < 6; i++) {
            runtime.dispatch(ROUND_START);
        }
        assertEquals(3, host.count("boom"), "runs exactly maxConsecutiveErrors times");
        assertEquals(1, host.count("disabled after 3 errors"));
    }

    @Test
    void aSuccessResetsTheErrorCount() {
        LuaRuntime runtime = main("""
                local n = 0
                events.on('round_start', function() n = n + 1; if n % 3 ~= 0 then error('flaky') end end)
                """);
        for (int i = 0; i < 9; i++) {
            runtime.dispatch(ROUND_START);
        }
        assertEquals(0, host.count("disabled"), "two failures then a success never reaches 3 in a row");
    }

    @Test
    void requireLoadsAModuleOnceAndReturnsIt() {
        build(Map.of(
                "test:main", """
                        local a = require('test:util')
                        local b = require('test:util')
                        log.info('same=' .. tostring(a == b) .. ' loads=' .. LOADS .. ' value=' .. a.answer)
                        """,
                "test:util", "LOADS = (LOADS or 0) + 1\nreturn { answer = 42 }"), List.of());
        assertEquals(1, host.count("same=true loads=1 value=42"), host.logs::toString);
    }

    @Test
    void requireCyclesAreReportedWithTheChain() {
        LuaRuntime runtime = build(Map.of(
                "test:main", "require('test:a')",
                "test:a", "require('test:b')",
                "test:b", "require('test:a')"), List.of());
        assertEquals(1, runtime.loadErrors().size());
        assertTrue(runtime.loadErrors().get(0).contains("require cycle: test:main -> test:a -> test:b -> test:a"), runtime.loadErrors()::toString);
    }

    @Test
    void eventDefsBindModuleFunctionsAndGlobalsAndRespectRoundFilters() {
        LuaRuntime runtime = build(Map.of(
                        "test:announcer", """
                                local M = {}
                                function M.on_round_start(e) command('say module round ' .. e.round) end
                                function late(e) command('say late round ' .. e.round) end
                                return M
                                """),
                List.of(new EventDef("round_start", "test:announcer", "on_round_start", EventDef.Filter.NONE, 0),
                        new EventDef("round_start", "test:announcer", "late", new EventDef.Filter(Optional.of(5), Optional.empty()), 1)));
        assertEquals(List.of(), runtime.loadErrors());
        host.round = 4;
        runtime.dispatch(new GameEvent.RoundStart(4));
        host.round = 5;
        runtime.dispatch(new GameEvent.RoundStart(5));
        assertEquals(List.of("say module round 4", "say module round 5", "say late round 5"), host.commands);
    }

    @Test
    void commandReturnsSuccessAndResult() {
        host.nextResult = new ScriptHost.CommandResult(false, 0);
        main("local ok, n = command('kill @e[type=zombie]'); log.info('ok=' .. tostring(ok) .. ' n=' .. n)");
        assertEquals(List.of("kill @e[type=zombie]"), host.commands);
        assertEquals(1, host.count("ok=false n=0"));
    }

    @Test
    void badBindingsAreLoadErrorsNotCrashes() {
        LuaRuntime runtime = build(Map.of("test:main", "events.on('round_strat', function() end)"),
                List.of(new EventDef("round_start", "test:missing", "f", EventDef.Filter.NONE, 0),
                        new EventDef("round_start", "test:main", "nope", EventDef.Filter.NONE, 0),
                        new EventDef("no_such_event", "test:main", "f", EventDef.Filter.NONE, 0)));
        List<String> errors = runtime.loadErrors();
        assertEquals(4, errors.size(), errors::toString);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown event 'round_strat'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("no script 'test:missing'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("defines no function 'nope'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown event 'no_such_event'")));
    }

    @Test
    void stringRepIsCapped() {
        LuaRuntime runtime = main("local s = string.rep('ab', 100000)");
        assertEquals(1, runtime.loadErrors().size());
        assertTrue(runtime.loadErrors().get(0).contains("string.rep result too long"));
    }

    @Test
    void zombieKilledArrivesWithItsFields() {
        UUID mob = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID killer = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        LuaRuntime runtime = main("""
                events.on('zombie_killed', function(e)
                  log.info(table.concat({e.event, e.mob, e.killer, tostring(e.headshot), tostring(e.melee), e.x, e.y, e.z}, ','))
                end)
                """);
        runtime.dispatch(new GameEvent.ZombieKilled(mob, Optional.of(killer), true, false, new BlockPos(1, 2, 3)));
        assertEquals(1, host.count("zombie_killed," + mob + "," + killer + ",true,false,1,2,3"), host.logs::toString);
    }

    @Test
    void gameQueriesSeeTheHost() {
        main("local p = game.players()[1]; log.info(p.name .. ' ' .. p.points .. ' ' .. tostring(p.alive) .. ' r' .. game.round())");
        assertEquals(1, host.count("Alice 750 true r1"), host.logs::toString);
    }

    /** Another mod's Lua state, such as TacZ's gun scripts: the same LuaJ, with its own string library. */
    private static Globals otherModsState() {
        Globals globals = new Globals();
        globals.load(new BaseLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        LuaC.install(globals);
        return globals;
    }

    private static String run(Globals globals, String code) {
        return globals.load(code, "@other_mod", globals).call().tojstring();
    }

    /** Runs with LuaJ's JVM-wide string metatable slot empty, as when the game starts, and puts it back after. */
    private static void fromAnEmptySlot(Runnable body) {
        LuaValue previous = LuaString.s_metatable;
        LuaString.s_metatable = null;
        try {
            body.run();
        } finally {
            LuaString.s_metatable = previous;
        }
    }

    /** What another mod's state does with string methods: an uncapped rep, dump, and a method it adds itself. */
    private static final String OTHER_MODS_STRING_USE = "getmetatable('').__index.bang = function(s) return s .. '!' end "
            + "return #('x'):rep(20000) .. ' ' .. tostring(('x').dump ~= nil) .. ' ' .. ('a'):upper() .. ' ' .. ('b'):bang()";

    @Test
    void methodCallRepIsCappedEvenWhenAnotherModLoadedStringsFirst() {
        fromAnEmptySlot(() -> {
            otherModsState();
            LuaRuntime runtime = main("""
                    log.info('method rep: ' .. tostring(pcall(function() return ('x'):rep(20000) end)))
                    log.info('method dump: ' .. tostring(('x').dump))
                    """);
            assertEquals(List.of(), runtime.loadErrors());
            assertEquals(1, host.count("method rep: false"), host.logs::toString);
            assertEquals(1, host.count("method dump: nil"), host.logs::toString);
        });
    }

    @Test
    void theRepCapCountsBytes() {
        LuaRuntime runtime = main("local s = string.rep(string.char(195, 169), 6000)");
        assertEquals(1, runtime.loadErrors().size(), "6000 two-byte characters are 12000 bytes, over the 10000 cap");
        assertTrue(runtime.loadErrors().get(0).contains("string.rep result too long (12000 > 10000)"), runtime.loadErrors()::toString);
    }

    @Test
    void aScriptCannotChangeAnotherModsStringMethods() {
        String attack = """
                pcall(function() getmetatable('').__index.upper = function() return 'PWNED' end end)
                pcall(function() getmetatable('').__index = { upper = function() return 'PWNED' end } end)
                string.upper = function() return 'PWNED' end
                """;
        fromAnEmptySlot(() -> {
            Globals first = otherModsState();
            main(attack);
            assertEquals("A", run(first, "return ('a'):upper()"), "a mod whose strings loaded before the runtime's");
        });
        fromAnEmptySlot(() -> {
            main(attack);
            Globals after = otherModsState();
            assertEquals("A", run(after, "return ('a'):upper()"), "a mod whose strings loaded after the runtime's");
        });
    }

    @Test
    void aScriptsOwnStringMethodsWork() {
        fromAnEmptySlot(() -> {
            otherModsState();
            main("""
                    function string.shout(s) return s:upper() .. '!' end
                    log.info(('hi'):shout() .. ' ' .. tostring(getmetatable('').__index == string))
                    """);
            assertEquals(1, host.count("HI! true"), host.logs::toString);
        });
    }

    @Test
    void otherModsKeepTheirStringMethodsEvenInsideAScriptsCommand() {
        fromAnEmptySlot(() -> {
            LuaRuntime runtime = main("""
                    string.shout = function(s) return 'mine' end
                    events.on('round_start', function() command('say hi') end)
                    """);
            Globals other = otherModsState();
            List<String> inside = new ArrayList<>();
            host.onCommand = command -> inside.add(run(other, OTHER_MODS_STRING_USE));
            runtime.dispatch(ROUND_START);
            assertEquals(List.of("20000 true A b!"), inside);
            assertEquals("20000 true A b!", run(other, OTHER_MODS_STRING_USE));
            assertEquals("false", run(other, "return tostring(pcall(function() return ('a'):shout() end))"), "the script's own method stays its own");
        });
    }

    @Test
    void runningOutOfMemoryStopsTheListenerAtOnce() {
        host.onCommand = command -> {
            throw new OutOfMemoryError("Java heap space");
        };
        LuaRuntime runtime = main("events.on('round_start', function() command('say hi') end)");
        runtime.dispatch(ROUND_START);
        runtime.dispatch(ROUND_START);
        assertEquals(1, host.commands.size(), "the listener runs once, not maxConsecutiveErrors times");
        assertEquals(1, host.count("test:main: out of memory"), host.logs::toString);
        assertEquals(1, host.count("disabled: it ran the server out of memory"), host.logs::toString);
    }

    @Test
    void doublingAStringWithConcatenationIsCapped() {
        LuaRuntime runtime = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> main("local s = 'x' for i = 1, 20 do s = s .. s end"));
        assertEquals(1, runtime.loadErrors().size(), "2^20 bytes is over the 10000 cap");
        assertTrue(runtime.loadErrors().get(0).contains("string too long"), runtime.loadErrors()::toString);
    }

    @Test
    void concatenationIsChargedAgainstTheBudget() {
        LuaRuntime runtime = main("local s = string.rep('x', 9000) for i = 1, 2000 do local t = s .. 'y' end");
        assertEquals(1, runtime.loadErrors().size(), "2000 copies of 9000 bytes cost more than 100000 instructions");
        assertTrue(runtime.loadErrors().get(0).contains("instruction budget"), runtime.loadErrors()::toString);
    }

    @Test
    void gsubFormatAndTableConcatResultsAreCapped() {
        LuaRuntime runtime = main("""
                local big = string.rep('x', 6000)
                local function try(name, f) local ok, e = pcall(f) log.info(name .. ': ' .. (ok and 'built' or tostring(e))) end
                try('gsub', function() return big:gsub('x', 'xx') end)
                try('format', function() return string.format('%s%s', big, big) end)
                try('concat', function() return table.concat({big, big}) end)
                local s, n = ('aaa'):gsub('a', 'b')
                log.info(s .. n .. ' ' .. ('%d-%s'):format(7, 'x') .. ' ' .. table.concat({'a', 'b'}, ','))
                """);
        assertEquals(List.of(), runtime.loadErrors());
        assertEquals(3, host.count("too long"), host.logs::toString);
        assertEquals(0, host.count("built"), host.logs::toString);
        assertEquals(1, host.count("bbb3 7-x a,b"), host.logs::toString);
    }

    @Test
    void errorsNameTheLineThatFailed() {
        LuaRuntime runtime = main("""
                local function f()
                  local a = 1
                  local b = nil
                  return b.x
                end
                f()
                """);
        assertEquals(1, runtime.loadErrors().size());
        assertTrue(runtime.loadErrors().get(0).contains("test:main:4 attempt to index"), runtime.loadErrors()::toString);
    }

    @Test
    void anEventFiredFromACommandDoesNotRefillTheBudget() {
        LuaRuntime[] runtime = new LuaRuntime[1];
        host.onCommand = command -> runtime[0].dispatch(new GameEvent.RoundEnd(1));
        runtime[0] = main("""
                events.on('round_end', function() end)
                events.on('round_start', function() while true do command('say again') end end)
                """);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runtime[0].dispatch(ROUND_START));
        assertEquals(1, host.logs.stream().filter(l -> l.contains("events.on('round_start')") && l.contains("instruction budget")).count(),
                host.logs::toString);
    }

    @Test
    void gameStartCountsAsRoundOneForFilters() {
        LuaRuntime runtime = build(Map.of("test:main", "function opening(e) command('say opening ' .. e.round) end"),
                List.of(new EventDef("game_start", "test:main", "opening", new EventDef.Filter(Optional.of(1), Optional.of(1)), 0)));
        host.round = 0;
        runtime.dispatch(new GameEvent.GameStart(List.of()));
        assertEquals(List.of("say opening 1"), host.commands);
    }

    /** round_start fires before the game enters the round, so the host still reports the previous round. */
    @Test
    void roundFiltersUseTheEventsRoundNotTheGamesCurrentOne() {
        LuaRuntime runtime = build(Map.of("test:main", "function late(e) command('say late ' .. e.round) end"),
                List.of(new EventDef("round_start", "test:main", "late", new EventDef.Filter(Optional.of(3), Optional.empty()), 0)));
        host.round = 2;
        runtime.dispatch(new GameEvent.RoundStart(3));
        host.round = 1;
        runtime.dispatch(new GameEvent.RoundStart(2));
        assertEquals(List.of("say late 3"), host.commands);
    }
}
