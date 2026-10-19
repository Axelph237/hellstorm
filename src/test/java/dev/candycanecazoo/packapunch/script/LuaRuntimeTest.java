package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.game.GameEvent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.time.Duration;
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

    @Test
    void buildingLeavesTheSharedStringMetatableAlone() {
        LuaValue previous = LuaString.s_metatable;
        LuaTable sentinel = new LuaTable();
        LuaString.s_metatable = sentinel;
        try {
            main("local x = 1");
            assertSame(sentinel, LuaString.s_metatable, "TacZ's Lua states share this metatable");
        } finally {
            LuaString.s_metatable = previous;
        }
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
