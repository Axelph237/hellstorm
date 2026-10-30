package dev.candycanecazoo.hellstorm.client.ui;

import dev.candycanecazoo.hellstorm.client.anim.Animation;
import dev.candycanecazoo.hellstorm.client.anim.Easing;
import dev.candycanecazoo.hellstorm.client.anim.Keyframes;
import dev.candycanecazoo.hellstorm.client.anim.Transition;
import dev.candycanecazoo.hellstorm.client.anim.UiClock;
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
    /**
     * How each row of the open list comes in: from transparent and {@link #ROW_SLIDE} pixels up to in place, each row
     * {@link #ROW_STAGGER} milliseconds after the one above, so they ladder down. Held at its start until a row's turn.
     */
    private static final Animation<Float> ROW_IN = Animation.of(Keyframes.floats().at(0, 0f).at(1, 1f), 140)
            .withEasing(Easing.EASE_OUT).withFill(Animation.Fill.BOTH);
    private static final long ROW_STAGGER = 30;
    private static final int ROW_SLIDE = 6;
    /** Minecraft draws text with almost no alpha as fully opaque, so a row's text waits until the row is this visible. */
    private static final float TEXT_SHOWN_FROM = 0.05f;

    private final List<T> values;
    private final Function<T, Component> name;
    private final Consumer<T> changed;
    private final Consumer<Dropdown<?>> opened;
    private final Transition<Float> hover = BoxButton.hoverTransition();
    private T value;
    private int scroll;
    private int listTop;
    private long openedAt;

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
        openedAt = UiClock.now();
        opened.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BoxButton.drawBox(graphics, this, hover, getX(), getY(), width, height);
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
        Font font = Minecraft.getInstance().font;
        long elapsed = UiClock.now() - openedAt;
        for (int i = 0; i < shown; i++) {
            T option = values.get(scroll + i);
            int y = listTop + i * height;
            // Hover and clicks use where the row ends up, so a quick click still lands where the player aimed.
            boolean hovered = mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + height;
            float in = ROW_IN.sample(elapsed - i * ROW_STAGGER, 1f);
            if (in <= 0) {
                continue;
            }
            int drawnY = y - Math.round(ROW_SLIDE * (1 - in));
            int background = Math.round((LIST_BACKGROUND >>> 24) * in) << 24;
            graphics.fill(getX(), drawnY, getX() + width, drawnY + height, background);
            graphics.setColor(1, 1, 1, in);
            UiKit.box(graphics, getX(), drawnY, width, height, false, hovered ? UiKit.Look.HOVERED : UiKit.Look.NORMAL);
            graphics.setColor(1, 1, 1, 1);
            if (in >= TEXT_SHOWN_FROM) {
                int color = option.equals(value) ? UiKit.ACCENT : UiKit.TEXT;
                renderScrollingString(graphics, font, name.apply(option), getX() + 4, drawnY, getX() + width - 4, drawnY + height,
                        Math.round(255 * in) << 24 | color);
            }
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
