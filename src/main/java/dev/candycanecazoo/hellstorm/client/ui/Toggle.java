package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** An on/off switch. */
public final class Toggle extends AbstractButton {
    private final Consumer<Boolean> changed;
    private boolean on;

    public Toggle(int x, int y, Component label, boolean on, Consumer<Boolean> changed) {
        super(x, y, UiKit.SWITCH_SIZE, UiKit.SWITCH_SIZE, label);
        this.on = on;
        this.changed = changed;
    }

    @Override
    public void onPress() {
        on = !on;
        changed.accept(on);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.Look look = !active ? UiKit.Look.DISABLED : isHovered() ? UiKit.Look.HOVERED : UiKit.Look.NORMAL;
        UiKit.toggle(graphics, getX(), getY(), on, isFocused(), look);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(getMessage(), CommonComponents.optionStatus(on)));
    }
}
