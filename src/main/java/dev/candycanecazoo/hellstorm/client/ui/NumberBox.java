package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A whole-number field whose box has a spinner in two corners: − in the bottom left, + in the top right. A click on one
 * steps the number by {@code step}, ten steps with Shift held, never below 0.
 */
public final class NumberBox extends BoxedEditBox {
    private static final long MAX = 999_999_999L;
    private final int step;

    public NumberBox(int x, int y, int width, int height, Component label, int step) {
        super(x, y, width, height, label);
        this.step = step;
        setMaxLength(9);
        setFilter(text -> text.chars().allMatch(Character::isDigit));
    }

    private boolean overMinus(double mouseX, double mouseY) {
        return mouseX >= boxX() && mouseX < boxX() + UiKit.SPINNER
                && mouseY >= boxY() + boxHeight() - UiKit.SPINNER && mouseY < boxY() + boxHeight();
    }

    private boolean overPlus(double mouseX, double mouseY) {
        return mouseX >= boxX() + boxWidth() - UiKit.SPINNER && mouseX < boxX() + boxWidth()
                && mouseY >= boxY() && mouseY < boxY() + UiKit.SPINNER;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && isVisible() && button == 0 && (overMinus(mouseX, mouseY) || overPlus(mouseX, mouseY))) {
            step(overPlus(mouseX, mouseY) ? step : -step);
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void step(int by) {
        long value = getValue().isEmpty() ? 0 : Long.parseLong(getValue());
        long stepped = value + (long) by * (Screen.hasShiftDown() ? 10 : 1);
        setValue(String.valueOf(Math.max(0, Math.min(MAX, stepped))));
    }

    @Override
    protected void renderBox(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = overBox(mouseX, mouseY);
        UiKit.spinnerBox(graphics, boxX(), boxY(), boxWidth(), boxHeight(), isFocused(), look(mouseX, mouseY),
                UiKit.SpinnerLook.of(!active, isFocused(), hovered, overMinus(mouseX, mouseY)),
                UiKit.SpinnerLook.of(!active, isFocused(), hovered, overPlus(mouseX, mouseY)));
    }
}
