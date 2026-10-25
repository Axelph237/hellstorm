package dev.candycanecazoo.hellstorm.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A box showing the chosen value; clicked, it opens a list of boxes stacked under it (over it near the bottom of the
 * screen), one per value, scrolling past {@link #VISIBLE}. The screen owns which dropdown is open, and gives the open
 * list its clicks, scrolls and drawing before anything else.
 */
public final class Dropdown<T> extends AbstractButton {
    private static final int VISIBLE = 6;
    private static final int CHEVRON = 5;
    private static final int LIST_BACKGROUND = 0xF0000000;

    private final List<T> values;
    private final Function<T, Component> name;
    private final Consumer<T> changed;
    private final Consumer<Dropdown<?>> opened;
    private T value;
    private int scroll;
    private int listTop;

    public Dropdown(int x, int y, int width, int height, Component label, List<T> values, T value, Function<T, Component> name,
                    Consumer<T> changed, Consumer<Dropdown<?>> opened) {
        super(x, y, width, height, label);
        this.values = values;
        this.value = value;
        this.name = name;
        this.changed = changed;
        this.opened = opened;
    }

    @Override
    public void onPress() {
        int chosen = values.indexOf(value);
        scroll = Mth.clamp(chosen - VISIBLE / 2, 0, maxScroll());
        opened.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.box(graphics, getX(), getY(), width, height);
        if (isHoveredOrFocused()) {
            UiKit.hover(graphics, getX(), getY(), width, height);
        }
        renderScrollingString(graphics, Minecraft.getInstance().font, name.apply(value), getX() + 4, getY(), getX() + width - CHEVRON - 8, getY() + height, UiKit.TEXT);
        int cx = getX() + width - CHEVRON - 5;
        int cy = getY() + (height - 3) / 2;
        for (int row = 0; row < 3; row++) {
            graphics.fill(cx + row, cy + row, cx + CHEVRON - row, cy + row + 1, 0xFF000000 | UiKit.TEXT);
        }
    }

    /** Draws the open list. Call last, so it covers the rows below. */
    public void renderList(GuiGraphics graphics, int mouseX, int mouseY, int screenHeight) {
        int shown = Math.min(VISIBLE, values.size());
        int below = getY() + height;
        listTop = below + shown * height <= screenHeight ? below : getY() - shown * height;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(getX(), listTop, getX() + width, listTop + shown * height, LIST_BACKGROUND);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < shown; i++) {
            T option = values.get(scroll + i);
            int y = listTop + i * height;
            UiKit.box(graphics, getX(), y, width, height);
            if (mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + height) {
                UiKit.hover(graphics, getX(), y, width, height);
            }
            renderScrollingString(graphics, font, name.apply(option), getX() + 4, y, getX() + width - 4, y + height,
                    option.equals(value) ? UiKit.ACCENT : UiKit.TEXT);
        }
        graphics.pose().popPose();
    }

    /** A click while the list is open: picks the value under it, if any. Either way the list closes. */
    public void clickList(double mouseX, double mouseY) {
        int shown = Math.min(VISIBLE, values.size());
        if (mouseX < getX() || mouseX >= getX() + width || mouseY < listTop || mouseY >= listTop + shown * height) {
            return;
        }
        T picked = values.get(scroll + (int) ((mouseY - listTop) / height));
        if (!picked.equals(value)) {
            value = picked;
            changed.accept(picked);
        }
    }

    public void scrollList(double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, values.size() - VISIBLE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(getMessage(), name.apply(value)));
    }
}
