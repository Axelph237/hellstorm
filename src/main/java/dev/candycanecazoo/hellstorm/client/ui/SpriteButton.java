package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A button that is one sprite at its own size, such as a half of the spinner. */
public final class SpriteButton extends AbstractButton {
    private final UiKit.Sprite sprite;
    private final Runnable pressed;

    public SpriteButton(UiKit.Sprite sprite, int x, int y, Component narration, Runnable pressed) {
        super(x, y, sprite.width(), sprite.height(), narration);
        this.sprite = sprite;
        this.pressed = pressed;
    }

    @Override
    public void onPress() {
        pressed.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.sprite(graphics, sprite, getX(), getY());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
