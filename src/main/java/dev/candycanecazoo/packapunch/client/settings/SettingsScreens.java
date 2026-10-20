package dev.candycanecazoo.packapunch.client.settings;

import dev.candycanecazoo.packapunch.feature.MysteryBoxBlock;
import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.feature.PowerSwitchBlock;
import dev.candycanecazoo.packapunch.feature.TrapControllerBlock;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** Opens the right settings screen for a machine, and closes it once the server accepts the settings. */
public final class SettingsScreens {
    private SettingsScreens() {}

    public static void open(OpenSettingsPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntity entity = mc.level == null ? null : mc.level.getBlockEntity(packet.pos());
        Screen screen = null;
        if (entity instanceof WallBuyBlock.Entity wallBuy) {
            screen = new WallBuyScreen(wallBuy, packet);
        } else if (entity instanceof PerkMachineBlock.Entity perk) {
            screen = new PerkMachineScreen(perk, packet);
        } else if (entity instanceof MysteryBoxBlock.Entity box) {
            screen = new MysteryBoxScreen(box, packet);
        } else if (entity instanceof PapMachineBlock.Entity pap) {
            screen = new PapMachineScreen(pap, packet);
        } else if (entity instanceof PowerSwitchBlock.Entity power) {
            screen = new PowerSwitchScreen(power);
        } else if (entity instanceof TrapControllerBlock.Entity trap) {
            screen = new TrapScreen(trap, packet);
        }
        if (screen != null) {
            mc.setScreen(screen);
        }
    }

    public static void saved(BlockPos pos, Optional<String> problem) {
        if (Minecraft.getInstance().screen instanceof SettingsScreen<?> screen && screen.pos().equals(pos)) {
            problem.ifPresentOrElse(screen::showProblem, screen::onClose);
        }
    }
}
