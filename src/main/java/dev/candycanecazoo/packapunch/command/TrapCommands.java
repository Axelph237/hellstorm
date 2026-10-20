package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.packapunch.feature.TrapControllerBlock;
import dev.candycanecazoo.packapunch.game.GameManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * {@code /pap trap cost|active|cooldown <n|default>}, {@code power <bool>}, {@code signal <1-15>}, {@code describe}: settings
 * for the trap you're looking at.
 */
final class TrapCommands {
    private TrapCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> trap() {
        return Commands.literal("trap")
                .then(optional("cost", 0, TrapControllerBlock.Config::withCost))
                .then(optional("active", 1, TrapControllerBlock.Config::withActiveTicks))
                .then(optional("cooldown", 0, TrapControllerBlock.Config::withCooldownTicks))
                .then(Commands.literal("power").then(Commands.argument("required", BoolArgumentType.bool())
                        .executes(ctx -> apply(ctx, c -> c.withPower(BoolArgumentType.getBool(ctx, "required"))))))
                .then(Commands.literal("signal").then(Commands.argument("strength", IntegerArgumentType.integer(1, 15))
                        .executes(ctx -> apply(ctx, c -> c.withSignal(IntegerArgumentType.getInteger(ctx, "strength"))))))
                .then(Commands.literal("describe").executes(ctx -> done(ctx, target(ctx))));
    }

    /** {@code <name> default} clears the setting back to the game's default; {@code <name> <n>} sets it. */
    private static LiteralArgumentBuilder<CommandSourceStack> optional(String name, int min,
                                                                        java.util.function.BiFunction<TrapControllerBlock.Config, Optional<Integer>, TrapControllerBlock.Config> with) {
        Function<Optional<Integer>, UnaryOperator<TrapControllerBlock.Config>> set = value -> c -> with.apply(c, value);
        return Commands.literal(name)
                .then(Commands.literal("default").executes(ctx -> apply(ctx, set.apply(Optional.empty()))))
                .then(Commands.argument("value", IntegerArgumentType.integer(min))
                        .executes(ctx -> apply(ctx, set.apply(Optional.of(IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    private static TrapControllerBlock.Entity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Targets.lookedAt(ctx.getSource().getPlayerOrException(), TrapControllerBlock.Entity.class, "trap controller");
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, UnaryOperator<TrapControllerBlock.Config> change) throws CommandSyntaxException {
        TrapControllerBlock.Entity trap = target(ctx);
        Targets.require(trap.trySet(change.apply(trap.config())));
        return done(ctx, trap);
    }

    private static int done(CommandContext<CommandSourceStack> ctx, TrapControllerBlock.Entity trap) {
        ctx.getSource().sendSuccess(() -> TrapControllerBlock.describe(trap, GameManager.get(ctx.getSource().getLevel())
                .map(a -> a.game().settings().traps()).orElseGet(() -> GameManager.settings().traps())), false);
        return 1;
    }
}
