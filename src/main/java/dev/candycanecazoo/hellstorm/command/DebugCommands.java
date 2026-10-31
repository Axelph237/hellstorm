package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.game.Cheats;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.tools.DebugRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/** `/hs debug [rule [true|false]]`: lists, shows, or sets the per-world debug rules, toggles per-player cheats, and skips to a round. */
final class DebugCommands {
    private DebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> debug() {
        var debug = Commands.literal("debug").executes(CommandErrors.titled("Unable to list debug rules", ctx -> {
            DebugRules rules = DebugRules.get(ctx.getSource().getServer());
            for (DebugRules.Rule rule : DebugRules.Rule.values()) {
                ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + rules.get(rule)), false);
            }
            return 1;
        }));
        for (DebugRules.Rule rule : DebugRules.Rule.values()) {
            debug.then(Commands.literal(rule.id())
                    .executes(CommandErrors.titled("Unable to show debug rule", ctx -> {
                        boolean value = DebugRules.get(ctx.getSource().getServer()).get(rule);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " = " + value), false);
                        return value ? 1 : 0;
                    }))
                    .then(Commands.argument("value", BoolArgumentType.bool()).executes(CommandErrors.titled("Unable to set debug rule", ctx -> {
                        boolean value = BoolArgumentType.getBool(ctx, "value");
                        DebugRules.get(ctx.getSource().getServer()).set(rule, value);
                        ctx.getSource().sendSuccess(() -> Component.literal(rule.id() + " is now " + value), true);
                        return 1;
                    }))));
        }
        debug.then(Commands.literal("round").then(Commands.argument("round", IntegerArgumentType.integer(1, 10_000))
                .executes(CommandErrors.titled("Unable to set the round", DebugCommands::round))));
        for (Cheats.Cheat cheat : Cheats.Cheat.values()) {
            debug.then(Commands.literal(cheat.id())
                    .then(Commands.argument("on", BoolArgumentType.bool())
                            .executes(CommandErrors.titled("Unable to set cheat", ctx -> cheat(ctx, cheat, List.of(ctx.getSource().getPlayerOrException()))))
                            .then(Commands.argument("players", EntityArgument.players())
                                    .executes(CommandErrors.titled("Unable to set cheat", ctx -> cheat(ctx, cheat, EntityArgument.getPlayers(ctx, "players")))))));
        }
        return debug;
    }

    /**
     * {@code /hs debug round <round>}: the game in this world skips to that round now, the zombies alive gone without
     * points or drops.
     */
    private static int round(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int round = IntegerArgumentType.getInteger(ctx, "round");
        GameManager.Active active = GameManager.get(ctx.getSource().getLevel())
                .orElseThrow(() -> CommandErrors.fail("No game is running in this world"));
        if (!active.game().jumpToRound(active.world(), round)) {
            throw CommandErrors.fail("The game hasn't reached round 1 yet, or is over");
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Now round " + round), true);
        return round;
    }

    /** {@code /hs debug infinite_money|god <true|false> [players]}: per-player testing aids, cleared on restart. */
    private static int cheat(CommandContext<CommandSourceStack> ctx, Cheats.Cheat cheat, Collection<ServerPlayer> players) {
        boolean on = BoolArgumentType.getBool(ctx, "on");
        players.forEach(p -> Cheats.set(p.getUUID(), cheat, on));
        String who = players.size() == 1 ? players.iterator().next().getGameProfile().getName() : players.size() + " players";
        ctx.getSource().sendSuccess(() -> Component.literal(cheat.id() + " is now " + (on ? "on" : "off") + " for " + who), true);
        return players.size();
    }
}
