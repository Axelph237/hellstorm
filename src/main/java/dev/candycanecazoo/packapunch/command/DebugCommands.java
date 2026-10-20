package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.candycanecazoo.packapunch.game.Cheats;
import dev.candycanecazoo.packapunch.tools.DebugRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/** `/pap debug [rule [true|false]]`: lists, shows, or sets the per-world debug rules, and toggles per-player cheats. */
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
        for (Cheats.Cheat cheat : Cheats.Cheat.values()) {
            debug.then(Commands.literal(cheat.id())
                    .then(Commands.argument("on", BoolArgumentType.bool())
                            .executes(ctx -> cheat(ctx, cheat, List.of(ctx.getSource().getPlayerOrException())))
                            .then(Commands.argument("players", EntityArgument.players())
                                    .executes(ctx -> cheat(ctx, cheat, EntityArgument.getPlayers(ctx, "players"))))));
        }
        return debug;
    }

    /** {@code /pap debug infinite_money|god <true|false> [players]}: per-player testing aids, cleared on restart. */
    private static int cheat(CommandContext<CommandSourceStack> ctx, Cheats.Cheat cheat, Collection<ServerPlayer> players) {
        boolean on = BoolArgumentType.getBool(ctx, "on");
        players.forEach(p -> Cheats.set(p.getUUID(), cheat, on));
        String who = players.size() == 1 ? players.iterator().next().getGameProfile().getName() : players.size() + " players";
        ctx.getSource().sendSuccess(() -> Component.literal(cheat.id() + " is now " + (on ? "on" : "off") + " for " + who), true);
        return players.size();
    }
}
