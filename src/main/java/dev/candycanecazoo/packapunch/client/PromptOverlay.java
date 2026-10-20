package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.Prompted;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * "Press [Use] to buy AK-47 [1000]" under the crosshair while a player in a game looks at something they can buy, in red
 * with the reason when they can't afford it or it needs power.
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
        prompt(mc).ifPresent(prompt -> {
            Component line = line(mc, prompt);
            graphics.drawCenteredString(mc.font, line, width / 2, height / 2 + 14, 0xFFFFFF);
        });
    }

    /** What the crosshair is on, if it is a machine with something to sell. Doors join in through the map, not here. */
    static Optional<Prompted.Prompt> prompt(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return mc.level.getBlockEntity(hit.getBlockPos()) instanceof Prompted prompted ? prompted.prompt() : Optional.empty();
    }

    static Component line(Minecraft mc, Prompted.Prompt prompt) {
        MutableComponent line = Component.literal("Press ").append(mc.options.keyUse.getTranslatedKeyMessage())
                .append(" or ").append(UseKey.KEY.getTranslatedKeyMessage())
                .append(" to " + prompt.what() + (prompt.cost() > 0 ? " [" + prompt.cost() + "]" : ""));
        if (prompt.needsPower() && !HudState.powerOn()) {
            return line.withStyle(ChatFormatting.GRAY).append(Component.literal("  The power must be on").withStyle(ChatFormatting.RED));
        }
        if (HudState.points() < prompt.cost()) {
            return line.withStyle(ChatFormatting.GRAY).append(Component.literal("  Not enough points").withStyle(ChatFormatting.RED));
        }
        return line;
    }
}
