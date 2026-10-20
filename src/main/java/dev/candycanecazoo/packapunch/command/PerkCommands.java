package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import dev.candycanecazoo.packapunch.perk.PerkDefs;
import dev.candycanecazoo.packapunch.perk.PerkEffect;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** {@code /pap perk set <perk>} for the perk machine you're looking at, and {@code /pap perk list}. */
final class PerkCommands {
    private PerkCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> perk() {
        return Commands.literal("perk")
                .then(Commands.literal("set").then(Commands.argument("perk", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(PerkDefs.all().keySet(), builder))
                        .executes(PerkCommands::set)))
                .then(Commands.literal("list").executes(PerkCommands::list));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PerkMachineBlock.Entity machine = Targets.lookedAt(ctx.getSource().getPlayerOrException(), PerkMachineBlock.Entity.class, "perk machine");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "perk");
        Targets.require(machine.trySet(new PerkMachineBlock.Config(Optional.of(id))));
        PerkDef def = PerkDefs.get(id).orElseThrow();
        ctx.getSource().sendSuccess(() -> Component.literal("This machine now sells ").append(Component.translatable(def.name()))
                .append(" for " + def.cost()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Map<ResourceLocation, PerkDef> perks = PerkDefs.all();
        if (perks.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No perks are loaded. Add some under data/<namespace>/packapunch/perks/"));
            return 0;
        }
        perks.forEach((id, def) -> ctx.getSource().sendSuccess(() -> Component.literal(id + ": ")
                .append(Component.translatable(def.name()).withStyle(s -> s.withColor(def.color())))
                .append(", " + def.cost() + " points" + (def.requiresPower() ? ", needs power" : "") + " ("
                        + def.effects().stream().map(PerkEffect::type).collect(Collectors.joining(", ")) + ")"), false));
        return perks.size();
    }
}
