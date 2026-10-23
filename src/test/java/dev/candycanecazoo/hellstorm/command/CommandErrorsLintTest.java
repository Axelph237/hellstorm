package dev.candycanecazoo.hellstorm.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every command fails the one way {@link CommandErrors} shows: registered through {@link CommandErrors#titled}, and failing
 * by throwing {@link CommandErrors#fail}, never by sending its own failure or making its own exception type.
 */
class CommandErrorsLintTest {
    private static final Path SOURCES = Path.of("src/main/java/dev/candycanecazoo/hellstorm");
    private static final Pattern UNTITLED = Pattern.compile("\\.executes\\((?!CommandErrors\\.titled\\()");
    private static final Pattern OWN_FAILURE = Pattern.compile("sendFailure\\(|CommandExceptionType");

    @Test
    void everyCommandIsTitledAndFailsThroughCommandErrors() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("CommandErrors.java")).toList()) {
                String[] lines = Files.readString(file).split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    check(UNTITLED.matcher(lines[i]), file, i, "registered without CommandErrors.titled", found);
                    if (!file.toString().contains("/gametest/")) {
                        check(OWN_FAILURE.matcher(lines[i]), file, i, "fails on its own instead of throwing CommandErrors.fail", found);
                    }
                }
            }
        }
        assertEquals(List.of(), found);
    }

    private static void check(Matcher matcher, Path file, int line, String what, List<String> found) {
        if (matcher.find()) {
            found.add(SOURCES.relativize(file) + ":" + (line + 1) + " " + what);
        }
    }
}
