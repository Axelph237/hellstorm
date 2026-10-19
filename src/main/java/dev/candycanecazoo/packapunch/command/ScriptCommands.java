package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.script.ScriptRegistry;
import dev.candycanecazoo.packapunch.script.ScriptRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * `/pap scripts reload` rebuilds the running game's Lua runtime. `/pap scripts check` reports load errors without running commands.
 * `/pap scripts rules [rule [value]]` lists, shows, or sets per-world script rules.
 */
final class ScriptCommands {
    private ScriptCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> script() {
        return Commands.literal("scripts")
                .then(Commands.literal("reload").executes(ScriptCommands::reload))
                .then(Commands.literal("check").executes(ScriptCommands::check))
                .then(rules());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rules() {
        var rules = Commands.literal("rules").executes(ctx -> {
            ScriptRules values = ScriptRules.get(ctx.getSource().getServer());
            for (ScriptRules.Rule rule : ScriptRules.Rule.values()) {
                ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + values.get(rule)), false);
            }
            return 1;
        });
        for (ScriptRules.Rule rule : ScriptRules.Rule.values()) {
            rules.then(Commands.literal(rule.id())
                    .executes(ctx -> {
                        boolean value = ScriptRules.get(ctx.getSource().getServer()).get(rule);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + value), false);
                        return value ? 1 : 0;
                    })
                    .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                        boolean value = BoolArgumentType.getBool(ctx, "value");
                        ScriptRules.get(ctx.getSource().getServer()).set(rule, value);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " is now " + value), true);
                        return 1;
                    })));
        }
        return rules;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No game here. Scripts load when a game opens; use /pap scripts check to test them"));
            return 0;
        }
        return report(ctx, active.get().scripts().rebuild(), "Scripts reloaded");
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        int scripts = ScriptRegistry.scripts().size();
        int defs = ScriptRegistry.eventDefs().size();
        if (scripts == 0 && defs == 0) {
            ctx.getSource().sendFailure(Component.literal("No scripts or event defs are loaded. Put a datapack in this world's datapacks folder "
                    + "with files under data/<namespace>/packapunch/scripts/*.lua or data/<namespace>/packapunch/events/*.json, then /reload"));
            return 0;
        }
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        List<String> errors = active.isPresent()
                ? active.get().scripts().checkOnly()
                : List.of();
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(scripts + " scripts and " + defs
                    + " event defs loaded. Join a game (/pap join) to check them against a map"), false);
            return 1;
        }
        return report(ctx, errors, scripts + " scripts and " + defs + " event defs load cleanly");
    }

    private static int report(CommandContext<CommandSourceStack> ctx, List<String> errors, String ok) {
        if (errors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(ok).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        errors.forEach(e -> ctx.getSource().sendFailure(Component.literal(e)));
        return 0;
    }
}
