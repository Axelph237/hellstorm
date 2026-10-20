package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.packapunch.data.BoxPool;
import dev.candycanecazoo.packapunch.data.BoxPools;
import dev.candycanecazoo.packapunch.feature.MysteryBoxBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;

import java.util.Optional;
import java.util.stream.Stream;

/** {@code /pap box pool <id>}, {@code cost <points|default>}, {@code describe}: settings for the box location you're looking at. */
final class BoxCommands {

    private BoxCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> box() {
        return Commands.literal("box")
                .then(Commands.literal("pool").then(Commands.argument("pool", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                Stream.concat(Stream.of(BoxPool.DEFAULT_ID), BoxPools.all().keySet().stream()).distinct(), builder))
                        .executes(BoxCommands::pool)))
                .then(Commands.literal("cost")
                        .then(Commands.literal("default").executes(ctx -> cost(ctx, Optional.empty())))
                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(ctx -> cost(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "points"))))))
                .then(Commands.literal("describe").executes(ctx -> done(ctx, target(ctx))));
    }

    private static MysteryBoxBlock.Entity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Targets.lookedAt(ctx.getSource().getPlayerOrException(), MysteryBoxBlock.Entity.class, "Mystery Box");
    }

    private static int pool(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MysteryBoxBlock.Entity entity = target(ctx);
        return apply(ctx, entity, new MysteryBoxBlock.Config(ResourceLocationArgument.getId(ctx, "pool"), entity.config().cost()));
    }

    private static int cost(CommandContext<CommandSourceStack> ctx, Optional<Integer> cost) throws CommandSyntaxException {
        MysteryBoxBlock.Entity entity = target(ctx);
        return apply(ctx, entity, new MysteryBoxBlock.Config(entity.config().pool(), cost));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, MysteryBoxBlock.Entity entity, MysteryBoxBlock.Config config) throws CommandSyntaxException {
        Targets.require(entity.trySet(config));
        return done(ctx, entity);
    }

    private static int done(CommandContext<CommandSourceStack> ctx, MysteryBoxBlock.Entity entity) {
        ctx.getSource().sendSuccess(() -> MysteryBoxBlock.describe(entity), false);
        return 1;
    }
}
