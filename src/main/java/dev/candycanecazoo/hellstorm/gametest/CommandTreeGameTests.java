package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Every command lives under /hs: the old /pap root must not come back, the upgrader's own subcommand is /hs upgrader, the
 * game's commands are under /hs game, and rooms, doors, and windows are under /hs zones.
 */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandTreeGameTests {
    private CommandTreeGameTests() {}

    @GameTest(template = "floor", batch = "command_tree")
    public static void commandsAreUnderHs(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        helper.assertTrue(root.getChild("hs") != null, "no /hs command");
        helper.assertTrue(root.getChild("pap") == null, "the old /pap root is still registered");
        var hs = root.getChild("hs");
        helper.assertTrue(hs.getChild("game").getChild("join") != null && hs.getChild("upgrader") != null,
                "/hs should hold the game commands under /hs game and the upgrader's subcommand");
        helper.assertTrue(hs.getChild("zones").getChild("add").getChild("door") != null && hs.getChild("undo") != null && hs.getChild("redo") != null,
                "/hs should hold /hs zones add door, /hs undo, and /hs redo");
        for (String gone : new String[] {"join", "start", "zone", "door", "window"}) {
            helper.assertTrue(hs.getChild(gone) == null, "/hs " + gone + " is still registered");
        }
        helper.assertTrue(root.getChild("hs").getChild("pap") == null, "/hs pap is still registered; it is /hs upgrader now");
        helper.assertTrue(root.getChild("hsz") != null && root.getChild("hsz").getRedirect() == hs.getChild("zones"), "/hsz should be /hs zones");
        helper.assertTrue(hs.getChild("zones").getChild("desel") != null, "/hs zones desel should clear the selection");
        helper.succeed();
    }
}
