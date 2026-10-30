package dev.candycanecazoo.hellstorm.client.ui;

import dev.candycanecazoo.hellstorm.client.anim.Easing;
import dev.candycanecazoo.hellstorm.client.anim.Transition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A button drawn as the input box, its text centred. */
public final class BoxButton extends AbstractButton {
    /** How long a box takes to ease into or out of its hovered look. */
    static final long HOVER_MILLIS = 120;
    private final Runnable pressed;
    private final Transition<Float> hover = hoverTransition();

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
        drawBox(graphics, this, hover, getX(), getY(), width, height);
        renderScrollingString(graphics, Minecraft.getInstance().font, 4, UiKit.TEXT);
    }

    static Transition<Float> hoverTransition() {
        return Transition.of(0, HOVER_MILLIS, Easing.EASE_OUT);
    }

    /** A button's box, easing into and out of its hovered look, pressed and disabled at once. */
    static void drawBox(GuiGraphics graphics, AbstractButton button, Transition<Float> hover, int x, int y, int width, int height) {
        hover.set(button.isHovered() ? 1f : 0f);
        UiKit.box(graphics, x, y, width, height, button.isFocused(), look(button), hover.value());
    }

    /** A button's look: pressed while it's held down (or disabled), hovered under the mouse. */
    static UiKit.Look look(AbstractButton button) {
        if (!button.active || button.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed()) {
            return UiKit.Look.PRESSED;
        }
        return button.isHovered() ? UiKit.Look.HOVERED : UiKit.Look.NORMAL;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
