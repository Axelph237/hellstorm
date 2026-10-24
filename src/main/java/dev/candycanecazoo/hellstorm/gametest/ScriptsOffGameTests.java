package dev.candycanecazoo.hellstorm.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.script.EventDef;
import dev.candycanecazoo.hellstorm.script.ScriptRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** With {@code scripting.enabled} off, {@code /hs scripts reload} fails saying so, and {@code /hs scripts check} warns. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScriptsOffGameTests {
    private ScriptsOffGameTests() {}

    /** A fake player that keeps the chat sent to it. */
    private static final class Reader extends FakePlayer {
        final List<Component> said = new ArrayList<>();

        Reader(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
            said.add(message);
        }

        String heard() {
            return said.stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
        }
    }

    /** Runs {@code command} as an op with scripting off and one trivial script loaded, and returns its result and chat. */
    private static String withScriptingOff(GameTestHelper helper, Reader player, String command, int[] result) {
        boolean was = ServerConfig.SCRIPTING_ENABLED.get();
        Map<String, String> scripts = ScriptRegistry.scripts();
        List<EventDef> defs = ScriptRegistry.eventDefs();
        try {
            ScriptRegistry.replace(Map.of("gametest:main", "local x = 1"), List.of());
            ServerConfig.SCRIPTING_ENABLED.set(false);
            result[0] = helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);
            return player.heard();
        } finally {
            ServerConfig.SCRIPTING_ENABLED.set(was);
            ScriptRegistry.replace(scripts, defs);
        }
    }

    @GameTest(template = "floor", batch = "scripts_off_reload")
    public static void reloadingScriptsWithScriptingOffSaysSo(GameTestHelper helper) {
        Reader player = new Reader(helper.getLevel(), "pap_scripts_off_reload");
        GameManager.Active active = PurchaseGameTests.game(helper, player);
        int[] result = {-1};
        String heard;
        try {
            heard = withScriptingOff(helper, player, "hs scripts reload", result);
        } finally {
            GameManager.end(active);
        }
        helper.assertTrue(result[0] == 0, "reload should fail with scripting off, returned " + result[0]);
        helper.assertTrue(heard.contains("scripting.enabled"), "it should say scripting is off, said:" + heard);
        helper.assertFalse(heard.contains("Scripts reloaded"), "nor claim it reloaded");
        helper.succeed();
    }

    @GameTest(template = "floor", batch = "scripts_off_check")
    public static void checkingScriptsWithScriptingOffWarns(GameTestHelper helper) {
        Reader player = new Reader(helper.getLevel(), "pap_scripts_off_check");
        int[] result = {-1};
        String heard = withScriptingOff(helper, player, "hs scripts check", result);
        helper.assertTrue(result[0] == 1, "check still lints with scripting off, returned " + result[0]);
        helper.assertTrue(heard.contains("Scripting is off"), "and warns that games run none of them, said:" + heard);
        helper.succeed();
    }
}
