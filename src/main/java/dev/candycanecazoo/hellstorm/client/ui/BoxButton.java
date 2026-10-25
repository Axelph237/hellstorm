package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A button drawn as the input box, its text centred. */
public final class BoxButton extends AbstractButton {
    private final Runnable pressed;

    public BoxButton(int x, int y, int width, int height, Component text, Runnable pressed) {
        super(x, y, width, height, text);
        this.pressed = pressed;
    }

    @Override
    public void onPress() {
        pressed.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.box(graphics, getX(), getY(), width, height);
        if (isHoveredOrFocused()) {
            UiKit.hover(graphics, getX(), getY(), width, height);
        }
        renderScrollingString(graphics, Minecraft.getInstance().font, 4, UiKit.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
