package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.client.Embers;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.net.OpenSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

final class PapMachineScreen extends SettingsScreen<PapMachineBlock.Config> {
    private final int defaultCost;
    private final Embers embers = new Embers();
    private String cost;

    PapMachineScreen(PapMachineBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.translatable(PapMachineBlock.NAME), entity);
        defaultCost = packet.defaultCost();
        cost = entity.config().cost().map(String::valueOf).orElse("");
    }

    @Override
    protected void rows() {
        number(Component.literal("Cost"), cost, Component.literal("Default (" + defaultCost + ")"), v -> cost = v);
    }

    @Override
    protected DataResult<PapMachineBlock.Config> settings() {
        return DataResult.success(new PapMachineBlock.Config(optionalCost(cost)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!ClientConfig.EMBERS.get()) {
            return;
        }
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && widget.isHovered()) {
                embers.emitFrom(widget.getX(), widget.getX() + widget.getWidth(), widget.getY(), 2);
            }
        }
        embers.tick();
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackdrop(graphics, mouseX, mouseY, partialTick);
        if (ClientConfig.EMBERS.get()) {
            embers.resize(width, height);
            embers.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ClientConfig.EMBERS.get()) {
            embers.burst(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
