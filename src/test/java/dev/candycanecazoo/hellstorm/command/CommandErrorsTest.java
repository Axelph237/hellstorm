package dev.candycanecazoo.hellstorm.command;

import dev.candycanecazoo.hellstorm.util.Highlight;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandErrorsTest {
    private record Run(String text, Style style) {}

    /** The message's text in pieces, each with the style it's drawn in. */
    private static List<Run> runs(Component message) {
        List<Run> runs = new ArrayList<>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) {
                runs.add(new Run(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static boolean colored(Run run, ChatFormatting color) {
        return TextColor.fromLegacyFormat(color).equals(run.style().getColor());
    }

    @Test
    void aFailureIsARedUnderlinedTitleThenAnErrorLineWithItsDetailsInGold() {
        String error = Highlight.of("Loophole found between {} and {} at {}", "hall", "lab", new BlockPos(3, 64, -9));
        List<Run> runs = runs(CommandErrors.message("Unable to validate map", List.of(error), List.of()));
        assertEquals("Unable to validate map\nERROR: Loophole found between hall and lab at (3, 64, -9)",
                runs.stream().map(Run::text).reduce("", String::concat));

        Run title = runs.get(0);
        assertTrue(colored(title, ChatFormatting.RED) && title.style().isUnderlined(), "title: " + title);
        for (Run run : runs.subList(1, runs.size())) {
            assertFalse(run.style().isUnderlined(), "only the title is underlined: " + run);
        }
        Run label = runs.stream().filter(r -> r.text().equals("ERROR: ")).findFirst().orElseThrow();
        assertTrue(colored(label, ChatFormatting.RED), "label: " + label);
        for (String detail : List.of("hall", "lab", "(3, 64, -9)")) {
            Run run = runs.stream().filter(r -> r.text().equals(detail)).findFirst().orElseThrow(() -> new AssertionError(detail + " in " + runs));
            assertTrue(colored(run, ChatFormatting.GOLD), detail + " is gold: " + run);
        }
        Run text = runs.stream().filter(r -> r.text().equals("Loophole found between ")).findFirst().orElseThrow();
        assertTrue(colored(text, ChatFormatting.WHITE), "the rest is white: " + text);
    }

    @Test
    void warningsFollowTheErrorsInYellow() {
        List<Run> runs = runs(CommandErrors.message("Unable to join game", List.of("first"), List.of("second")));
        assertEquals("Unable to join game\nERROR: first\nWARNING: second", runs.stream().map(Run::text).reduce("", String::concat));
        assertTrue(colored(runs.stream().filter(r -> r.text().equals("WARNING: ")).findFirst().orElseThrow(), ChatFormatting.YELLOW));
    }

    @Test
    void highlightsFillInOrderAndCountTheirPlaces() {
        assertEquals("Room hall has no boxes", Highlight.plain(Highlight.of("Room {} has no boxes", "hall")));
        assertThrows(IllegalArgumentException.class, () -> Highlight.of("Room {} and {}", "hall"));
        assertThrows(IllegalArgumentException.class, () -> Highlight.of("Room {}", "hall", "lab"));
    }
}
