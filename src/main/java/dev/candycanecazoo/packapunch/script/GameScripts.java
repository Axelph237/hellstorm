package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.game.Game;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** The Lua runtime attached to one game. Rebuilt from the current datapacks on demand; dropped with the game. */
public final class GameScripts {
    private final ServerLevel level;
    private final Game game;
    private final BlockPos origin;
    private LuaRuntime runtime;

    private GameScripts(ServerLevel level, Game game, BlockPos origin) {
        this.level = level;
        this.game = game;
        this.origin = origin;
    }

    public static GameScripts attach(ServerLevel level, Game game, BlockPos origin) {
        GameScripts scripts = new GameScripts(level, game, origin);
        scripts.rebuild();
        game.events().subscribe(event -> {
            if (scripts.runtime != null) {
                scripts.runtime.dispatch(event);
            }
        });
        return scripts;
    }

    /** Builds a fresh runtime from the loaded datapacks. Returns its load errors. */
    public List<String> rebuild() {
        if (!ServerConfig.SCRIPTING_ENABLED.get()) {
            runtime = null;
            return List.of();
        }
        runtime = LuaRuntime.build(ScriptRegistry.scripts(), ScriptRegistry.eventDefs(), host(true), limits());
        return runtime.loadErrors();
    }

    /** Loads the current datapack scripts with commands switched off, to report problems without touching the world. */
    public List<String> checkOnly() {
        return LuaRuntime.build(ScriptRegistry.scripts(), ScriptRegistry.eventDefs(), host(false), limits()).loadErrors();
    }

    private ForgeScriptHost host(boolean commandsAllowed) {
        return new ForgeScriptHost(level, game, origin, ServerConfig.SCRIPT_PERMISSION_LEVEL.get(), commandsAllowed);
    }

    private static LuaRuntime.Limits limits() {
        return new LuaRuntime.Limits(ServerConfig.SCRIPT_INSTRUCTION_BUDGET.get(), ServerConfig.SCRIPT_MAX_CONSECUTIVE_ERRORS.get(),
                ServerConfig.SCRIPT_MAX_STRING_LENGTH.get());
    }
}
