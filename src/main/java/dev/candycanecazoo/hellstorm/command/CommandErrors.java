package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.candycanecazoo.hellstorm.util.Highlight;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * How every {@code /hs} command fails. A command throws {@link #fail} with what went wrong, its ids, names, numbers, and
 * positions marked ({@link Highlight}). The {@link #titled} wrapper it is registered with turns that, or any other error it
 * meets, into one chat message:
 * <pre>
 * Unable to validate map            (red, underlined)
 * ERROR: Loophole found between hall and lab at (3, 64, 9)   ("ERROR:" red, the message white, its details gold)
 * </pre>
 */
public final class CommandErrors {
    private static final SimpleCommandExceptionType TYPE = new SimpleCommandExceptionType(Component.literal("Command failed"));

    /** A command's own failure: an ERROR line for each of {@code errors}, then a WARNING line for each of {@code warnings}. */
    public static final class Failure extends CommandSyntaxException {
        private final List<String> errors;
        private final List<String> warnings;

        private Failure(List<String> errors, List<String> warnings) {
            super(TYPE, Component.literal(Highlight.plain(String.join("; ", errors))));
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }
    }

    private CommandErrors() {}

    /** A failure reading {@code template} with each {@code {}} filled by the next of {@code details}, in gold. */
    public static Failure fail(String template, Object... details) {
        return new Failure(List.of(Highlight.of(template, details)), List.of());
    }

    /** A failure with an ERROR line for each of {@code errors} and a WARNING line for each of {@code warnings}, already marked. */
    public static Failure failAll(List<String> errors, List<String> warnings) {
        return new Failure(errors, warnings);
    }

    /** {@code command}, its failures shown under {@code title}, such as "Unable to validate map". */
    public static Command<CommandSourceStack> titled(String title, Command<CommandSourceStack> command) {
        return ctx -> {
            try {
                return command.run(ctx);
            } catch (Failure failure) {
                throw new CommandSyntaxException(TYPE, message(title, failure.errors, failure.warnings));
            } catch (CommandSyntaxException other) {
                // Minecraft's own, such as "A player is required to run this command here".
                throw new CommandSyntaxException(TYPE, message(title, List.of(other.getRawMessage().getString()), List.of()));
            }
        };
    }

    /** The title, then an ERROR line for each error and a WARNING line for each warning. */
    public static Component message(String title, List<String> errors, List<String> warnings) {
        MutableComponent out = Component.literal(title).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);
        errors.forEach(error -> out.append(newLine()).append(line("ERROR", ChatFormatting.RED, error)));
        warnings.forEach(warning -> out.append(newLine()).append(warning(warning)));
        return out;
    }

    private static Component newLine() {
        return Component.literal("\n").withStyle(style -> style.withUnderlined(false));
    }

    /** A WARNING line, for a report that went through with warnings. */
    public static MutableComponent warning(String message) {
        return line("WARNING", ChatFormatting.YELLOW, message);
    }

    /** {@code label} in its color, then the marked message in white with its details in gold. */
    public static MutableComponent line(String label, ChatFormatting color, String message) {
        return Component.literal(label + ": ").withStyle(style -> style.withColor(color).withUnderlined(false))
                .append(Highlight.component(message, ChatFormatting.WHITE).withStyle(style -> style.withUnderlined(false)));
    }
}
