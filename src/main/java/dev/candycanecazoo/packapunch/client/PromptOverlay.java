package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.Prompted;
import dev.candycanecazoo.packapunch.map.DoorDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * "AK-47 1000" under the crosshair while a player in a game looks at something they can buy, in red when they can't
 * afford it or it needs power.
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PromptOverlay {
    private PromptOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "prompt", (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
    }

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.inGame() || mc.level == null || mc.options.hideGui) {
            return;
        }
        prompt(mc).ifPresent(prompt -> WrappedText.drawCentered(graphics, mc.font, text(prompt), width / 2, height / 2 + 14, width,
                buyable(prompt) ? 0xFFFFFF : 0xFF5555));
    }

    /** What the crosshair is on, if it is a door or a machine with something to sell. */
    static Optional<Prompted.Prompt> prompt(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        Optional<Prompted.Prompt> door = ClientMapState.map().doorAt(hit.getBlockPos()).map(d -> new Prompted.Prompt(
                d.style() == DoorDef.Style.DEBRIS ? "Debris" : "Door", d.cost(), d.requiresPower()));
        if (door.isPresent()) {
            return door;
        }
        return mc.level.getBlockEntity(hit.getBlockPos()) instanceof Prompted prompted ? prompted.prompt() : Optional.empty();
    }

    static Component text(Prompted.Prompt prompt) {
        return Component.literal(prompt.cost() > 0 ? prompt.label() + " " + prompt.cost() : prompt.label());
    }

    static boolean buyable(Prompted.Prompt prompt) {
        return (!prompt.needsPower() || HudState.powerOn()) && HudState.points() >= prompt.cost();
    }
}
