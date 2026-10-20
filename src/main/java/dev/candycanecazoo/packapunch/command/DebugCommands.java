package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.candycanecazoo.packapunch.tools.DebugRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** `/pap debug [rule [true|false]]`: lists, shows, or sets the per-world debug rules. */
final class DebugCommands {
    private DebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> debug() {
        var debug = Commands.literal("debug").executes(ctx -> {
            DebugRules rules = DebugRules.get(ctx.getSource().getServer());
            for (DebugRules.Rule rule : DebugRules.Rule.values()) {
                ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + rules.get(rule)), false);
            }
            return 1;
        });
        for (DebugRules.Rule rule : DebugRules.Rule.values()) {
            debug.then(Commands.literal(rule.id())
                    .executes(ctx -> {
                        boolean value = DebugRules.get(ctx.getSource().getServer()).get(rule);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + value), false);
                        return value ? 1 : 0;
                    })
                    .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                        boolean value = BoolArgumentType.getBool(ctx, "value");
                        DebugRules.get(ctx.getSource().getServer()).set(rule, value);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " is now " + value), true);
                        return 1;
                    })));
        }
        return debug;
    }
}
