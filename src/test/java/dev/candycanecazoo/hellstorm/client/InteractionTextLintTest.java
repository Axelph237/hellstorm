package dev.candycanecazoo.hellstorm.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Interaction text must fit the screen: it goes through Notices and WrappedText, which wrap, never the raw action bar. */
class InteractionTextLintTest {
    private static final Pattern ACTION_BAR = Pattern.compile("displayClientMessage\\((?s:.*?), true\\)");
    private static final Pattern CENTERED = Pattern.compile("drawCenteredString\\(");

    private static List<Path> offenders(Pattern pattern) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return pattern.matcher(Files.readString(p)).find();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }

    @Test
    void messagesGoThroughNoticesNotTheActionBar() throws IOException {
        assertEquals(List.of(), offenders(ACTION_BAR), "use game/Notices.show: the action bar can't wrap, so long messages run off the screen");
    }

    @Test
    void centredHudTextWraps() throws IOException {
        assertEquals(List.of(), offenders(CENTERED), "use client/WrappedText.drawCentered: drawCenteredString doesn't wrap");
    }
}
