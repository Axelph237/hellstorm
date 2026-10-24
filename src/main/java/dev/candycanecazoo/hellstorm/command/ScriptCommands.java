package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.script.ScriptRegistry;
import dev.candycanecazoo.hellstorm.util.Highlight;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * `/hs scripts reload` rebuilds the running game's Lua runtime. `/hs scripts check` reports load errors without running commands.
 * Both say when {@code scripting.enabled} is off: reload fails, and check still lints but warns.
 */
final class ScriptCommands {
    private ScriptCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> script() {
        return Commands.literal("scripts")
                .then(Commands.literal("reload").executes(CommandErrors.titled("Unable to reload scripts", ScriptCommands::reload)))
                .then(Commands.literal("check").executes(CommandErrors.titled("Unable to check scripts", ScriptCommands::check)));
    }

    private static final String CONFIG_FILE = "serverconfig/hellstorm-server.toml";

    private static int reload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            throw CommandErrors.fail("No game here. Scripts load when a game opens; use {} to test them", "/hs scripts check");
        }
        // Rebuilt either way, so turning scripting off and reloading stops the scripts a game is already running.
        List<String> errors = active.get().scripts().rebuild();
        if (!ServerConfig.SCRIPTING_ENABLED.get()) {
            throw CommandErrors.fail("Scripting is off on this server ({} in {}), so this game runs no scripts", "scripting.enabled", CONFIG_FILE);
        }
        return report(ctx, errors, List.of(), "Scripts reloaded");
    }

    private static int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int scripts = ScriptRegistry.scripts().size();
        int defs = ScriptRegistry.eventDefs().size();
        if (scripts == 0 && defs == 0) {
            throw CommandErrors.fail("No scripts or event defs are loaded. Put a datapack in this world's datapacks folder "
                    + "with files under {} or {}, then {}", "data/<namespace>/hellstorm/scripts/*.lua", "data/<namespace>/hellstorm/events/*.json", "/reload");
        }
        List<String> warnings = ServerConfig.SCRIPTING_ENABLED.get() ? List.of()
                : List.of(Highlight.of("Scripting is off on this server ({} in {}), so games run none of these scripts", "scripting.enabled", CONFIG_FILE));
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(scripts + " scripts and " + defs
                    + " event defs loaded. Join a game (/hs game join) to check them against a map"), false);
            warnings.forEach(warning -> ctx.getSource().sendSuccess(() -> CommandErrors.warning(warning), false));
            return 1;
        }
        return report(ctx, active.get().scripts().checkOnly(), warnings, scripts + " scripts and " + defs + " event defs load cleanly");
    }

    private static int report(CommandContext<CommandSourceStack> ctx, List<String> errors, List<String> warnings, String ok) throws CommandSyntaxException {
        if (!errors.isEmpty()) {
            throw CommandErrors.failAll(errors, warnings);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(ok).withStyle(ChatFormatting.GREEN), false);
        warnings.forEach(warning -> ctx.getSource().sendSuccess(() -> CommandErrors.warning(warning), false));
        return 1;
    }
}
