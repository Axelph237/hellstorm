package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Every command lives under /hs: the old /pap root must not come back, while the upgrader's own subcommand is /hs upgrader. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandTreeGameTests {
    private CommandTreeGameTests() {}

    @GameTest(template = "floor", batch = "command_tree")
    public static void commandsAreUnderHs(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        helper.assertTrue(root.getChild("hs") != null, "no /hs command");
        helper.assertTrue(root.getChild("pap") == null, "the old /pap root is still registered");
        helper.assertTrue(root.getChild("hs").getChild("join") != null && root.getChild("hs").getChild("upgrader") != null,
                "/hs should hold the game commands and the upgrader's subcommand");
        helper.assertTrue(root.getChild("hs").getChild("pap") == null, "/hs pap is still registered; it is /hs upgrader now");
        helper.succeed();
    }
}
