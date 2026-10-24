package dev.candycanecazoo.hellstorm.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * The CoD names (Pack-a-Punch, Mystery Crate, the perks, the power-ups) must be renameable by a resource pack before a public
 * release, so text players see takes them from the lang file. This catches one written into a prompt, refusal, or
 * announcement, and a lang key used there that the lang file doesn't have.
 */
class MachineNamesLintTest {
    private static final Pattern PLAYER_TEXT = Pattern.compile("(?:new Prompt|Refusal\\.Because|\\.announce)\\(\"([^\"]*)\"");
    private static final Pattern NAMES = Pattern.compile("Pack-a-Punch|Pack-A-Punch|Mystery Crate|Vigoraid|Vigorade|Load-Up|RE:Load|Hellfire|Saint's Brew|Off-Hand|"
            + "Wandering Spirit|Jockey Energy|Insta-Kill|Max Ammo|Double Points|Fire Sale|Carpenter|Nuke", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY = Pattern.compile("\"((?:name|message)\\.hellstorm\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern CRATE_AS_BOX = Pattern.compile("\\bmystery box|(?<!/hs )\\bbox pools?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIG_COMMENT = Pattern.compile("\\.comment\\(\"([^\"]*)\"");
    private static final Pattern BOX = Pattern.compile("\\bbox(es)?\\b", Pattern.CASE_INSENSITIVE);

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/gametest/")).toList();
        }
    }

    @Test
    void playerTextNamesNoMachineByHand() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            Matcher text = PLAYER_TEXT.matcher(Files.readString(file));
            while (text.find()) {
                if (NAMES.matcher(text.group(1)).find()) {
                    offenders.add(file.getFileName() + ": \"" + text.group(1) + "\"");
                }
            }
        }
        assertEquals(List.of(), offenders, "use a name.hellstorm.* key (Refusal.Translated, GameWorld.announceNamed, Language for prompts)");
    }

    /**
     * Players and map makers know it as the Mystery Crate. Any string may say "box" for a selection box, so strings are only
     * checked for the crate's old names; config comments and the lang file never mean a selection box, so there "box" is out.
     */
    @Test
    void noTextCallsTheCrateABox() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            String source = Files.readString(file);
            Matcher text = STRING.matcher(source);
            while (text.find()) {
                if (CRATE_AS_BOX.matcher(text.group(1)).find()) {
                    offenders.add(file.getFileName() + ": \"" + text.group(1) + "\"");
                }
            }
            Matcher comment = CONFIG_COMMENT.matcher(source);
            while (comment.find()) {
                if (BOX.matcher(comment.group(1)).find()) {
                    offenders.add(file.getFileName() + " config comment: \"" + comment.group(1) + "\"");
                }
            }
        }
        JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/hellstorm/lang/en_us.json"))).getAsJsonObject();
        lang.entrySet().stream().filter(e -> BOX.matcher(e.getValue().getAsString()).find())
                .forEach(e -> offenders.add("en_us.json: " + e.getKey()));
        assertEquals(List.of(), offenders, "say Mystery Crate (in player text, name.hellstorm.mystery_crate)");
    }

    @Test
    void everyNameAndMessageKeyIsInTheLangFile() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/hellstorm/lang/en_us.json"))).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        for (Path file : sources()) {
            Matcher key = KEY.matcher(Files.readString(file));
            while (key.find()) {
                if (!lang.has(key.group(1))) {
                    missing.add(file.getFileName() + ": " + key.group(1));
                }
            }
        }
        for (String reason : List.of("not_punchable", "unknown_punch_id", "no_upgrade_path", "max_tier")) {
            if (!lang.has("message.hellstorm.pap." + reason)) {
                missing.add("PunchOutcome: message.hellstorm.pap." + reason);
            }
        }
        assertEquals(List.of(), missing);
    }
}
