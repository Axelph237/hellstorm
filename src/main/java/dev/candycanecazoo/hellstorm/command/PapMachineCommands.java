package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** {@code /hs upgrader cost <points|default>}: what the upgrader you're looking at charges. */
final class PapMachineCommands {
    private PapMachineCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> upgrader() {
        return Commands.literal("upgrader")
                .then(Commands.literal("cost")
                        .then(Commands.literal("default").executes(CommandErrors.titled("Unable to set upgrader cost", ctx -> cost(ctx, Optional.empty()))))
                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(CommandErrors.titled("Unable to set upgrader cost", ctx -> cost(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "points")))))));
    }

    private static int cost(CommandContext<CommandSourceStack> ctx, Optional<Integer> cost) throws CommandSyntaxException {
        PapMachineBlock.Entity machine = Targets.lookedAt(ctx.getSource().getPlayerOrException(), PapMachineBlock.Entity.class, "Dante's Toil");
        machine.setConfig(new PapMachineBlock.Config(cost));
        ctx.getSource().sendSuccess(() -> Component.translatable(PapMachineBlock.NAME).append(" now costs "
                + cost.map(String::valueOf).orElse("the default (pap.default_cost)")), false);
        return 1;
    }
}
