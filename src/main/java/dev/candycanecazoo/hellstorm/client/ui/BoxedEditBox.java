package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field drawn in the input box. The field itself is the box's inside, from the text's top line down, so the
 * text sits centred in the box; the box is drawn around it, focused while you type in it.
 */
public class BoxedEditBox extends EditBox {
    private static final int INSET = 5;
    private final int boxWidth;
    private final int boxHeight;
    private final int textTop;

    public BoxedEditBox(int x, int y, int width, int height, Component label) {
        super(Minecraft.getInstance().font, x + INSET, y + (height - 8) / 2, width - 2 * INSET, height - (height - 8) / 2 - 2, label);
        boxWidth = width;
        boxHeight = height;
        textTop = (height - 8) / 2;
        setBordered(false);
    }

    protected int boxX() {
        return getX() - INSET;
    }

    protected int boxY() {
        return getY() - textTop;
    }

    protected int boxWidth() {
        return boxWidth;
    }

    protected int boxHeight() {
        return boxHeight;
    }

    protected boolean overBox(double mouseX, double mouseY) {
        return mouseX >= boxX() && mouseX < boxX() + boxWidth && mouseY >= boxY() && mouseY < boxY() + boxHeight;
    }

    protected UiKit.Look look(int mouseX, int mouseY) {
        return !active ? UiKit.Look.DISABLED : overBox(mouseX, mouseY) ? UiKit.Look.HOVERED : UiKit.Look.NORMAL;
    }

    /** Draws the box round the field. */
    protected void renderBox(GuiGraphics graphics, int mouseX, int mouseY) {
        UiKit.box(graphics, boxX(), boxY(), boxWidth, boxHeight, isFocused(), look(mouseX, mouseY));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBox(graphics, mouseX, mouseY);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
