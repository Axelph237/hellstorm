package dev.candycanecazoo.packapunch.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Forge's event bus names the listener it generates for a {@code @SubscribeEvent} method after its package, the simple
 * name of its class, the method, and the event's simple name, and reuses a listener with the same name. Two methods that
 * share all four silently become one: the second never runs. (Pack-a-Punch's renderer went unregistered this way, behind
 * the power-up renderer's.)
 */
class EventSubscriberLintTest {
    private static final Pattern PACKAGE = Pattern.compile("^package ([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern SUBSCRIBER = Pattern.compile("@SubscribeEvent[^\\n]*\\n\\s*public static void (\\w+)\\(([\\w.]+)");

    @Test
    void noTwoSubscribersGetTheSameGeneratedName() throws IOException {
        Map<String, Path> names = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher pkg = PACKAGE.matcher(source);
                String packageName = pkg.find() ? pkg.group(1) : "";
                Matcher subscriber = SUBSCRIBER.matcher(source);
                while (subscriber.find()) {
                    String event = subscriber.group(2).substring(subscriber.group(2).lastIndexOf('.') + 1);
                    String name = packageName + "." + enclosingType(source, subscriber.start()) + "_" + subscriber.group(1) + "_" + event;
                    Path previous = names.putIfAbsent(name, file);
                    if (previous != null) {
                        clashes.add(name + " in " + previous + " and " + file);
                    }
                }
            }
        }
        assertEquals(List.of(), clashes, "rename the class or the method so each listener's name is unique");
    }

    /** The last type declared before {@code at}: the class the method belongs to, for this codebase's flat nesting. */
    private static String enclosingType(String source, int at) {
        Matcher type = TYPE.matcher(source);
        String last = "";
        while (type.find() && type.start() < at) {
            last = type.group(1);
        }
        return last;
    }
}
