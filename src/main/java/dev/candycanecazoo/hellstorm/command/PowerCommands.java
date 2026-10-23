package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.feature.PowerSwitchBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /hs power cost <points>}: what the power switch you're looking at costs to pull. */
final class PowerCommands {
    private PowerCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> power() {
        return Commands.literal("power")
                .then(Commands.literal("cost").then(Commands.argument("points", IntegerArgumentType.integer(0)).executes(CommandErrors.titled("Unable to set power switch cost", PowerCommands::cost))));
    }

    private static int cost(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PowerSwitchBlock.Entity entity = Targets.lookedAt(ctx.getSource().getPlayerOrException(), PowerSwitchBlock.Entity.class, "power switch");
        int cost = IntegerArgumentType.getInteger(ctx, "points");
        entity.setConfig(new PowerSwitchBlock.Config(cost));
        ctx.getSource().sendSuccess(() -> Component.literal("The power switch now costs " + cost), false);
        return 1;
    }
}
