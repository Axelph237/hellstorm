package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.client.WrappedText;
import dev.candycanecazoo.hellstorm.client.ui.BoxButton;
import dev.candycanecazoo.hellstorm.client.ui.BoxedEditBox;
import dev.candycanecazoo.hellstorm.client.anim.UiClock;
import dev.candycanecazoo.hellstorm.client.ui.Dropdown;
import dev.candycanecazoo.hellstorm.client.ui.Ladder;
import dev.candycanecazoo.hellstorm.client.ui.NumberBox;
import dev.candycanecazoo.hellstorm.client.ui.Toggle;
import dev.candycanecazoo.hellstorm.client.ui.UiKit;
import dev.candycanecazoo.hellstorm.feature.ConfigBlockEntity;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.SaveSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A machine's settings as a column of labelled rows inside the Hellstorm frame, with Done and Cancel. Subclasses keep
 * the values being edited in fields and lay out their rows in {@link #rows()}, which runs again whenever the screen is
 * rebuilt or {@link #relayout()} is called. Done sends the settings to the server, which checks them and either closes
 * the screen or sends back the problem.
 */
abstract class SettingsScreen<C> extends Screen {
    private static final int WIDTH = 240;
    private static final int LABEL_WIDTH = 96;
    private static final int HEIGHT = 20;
    private static final int GAP = 2;
    /** From the frame's outside to the rows: clear of its corner knots, with the title in the gap of its top edge. */
    private static final int PAD_SIDE = 24;
    private static final int PAD_TOP = 26;
    private static final int PAD_BOTTOM = 22;
    private static final int TITLE_Y = 2;
    /** The frame's two top corners, which the title stays between. */
    private static final int TITLE_CLEARANCE = 128;
    private static final int SLOT = 20;
    private static final int PROBLEM_LINE = 12;
    /** Done and Cancel keep left, so the frame's flames in its bottom right corner stay clear. */
    private static final int BUTTON_WIDTH = 90;
    private static final int PROBLEM_COLOR = 0xFF5555;
    /** The rows fade in one after another, top first, then Done and Cancel. */
    private static final Ladder ROWS_IN = Ladder.of(180, 40);

    private final BlockPos pos;
    private final Codec<C> codec;
    private final List<Text> texts = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private int nextRow;
    private int problemY;
    private int panelTop;
    private int panelHeight;
    private int top;
    private Optional<Component> problem = Optional.empty();
    private Dropdown<?> open;
    private boolean rebuild;
    /** Each widget's row, for the rows' fade in. Done and Cancel are the row after the last. */
    private final Map<Renderable, Integer> widgetRows = new IdentityHashMap<>();
    private int rowCount;
    /** The row widgets are being added to. */
    private int rowIndex;
    /** When the screen first opened; laying it out again later doesn't replay the fade. */
    private long openedAt = -1;

    private record Text(int x, int y, int row, Supplier<Component> text) {}

    private record Slot(int x, int y, int row, Supplier<ItemStack> stack) {}

    protected SettingsScreen(Component title, ConfigBlockEntity<C> entity) {
        super(title);
        this.pos = entity.getBlockPos();
        this.codec = entity.codec();
    }

    BlockPos pos() {
        return pos;
    }

    /** Adds this screen's rows with {@link #number}, {@link #choice}, {@link #toggle}, {@link #button}, {@link #item} and {@link #text}. */
    protected abstract void rows();

    /** The settings to send, or what's wrong with what was typed. */
    protected abstract DataResult<C> settings();

    @Override
    protected final void init() {
        texts.clear();
        slots.clear();
        widgetRows.clear();
        open = null;
        nextRow = 0;
        rowCount = 0;
        if (openedAt < 0) {
            openedAt = UiClock.now();
        }
        rows();
        rowIndex = rowCount;
        problemY = nextRow + 1;
        int buttons = problemY + PROBLEM_LINE + 2;
        addRenderableWidget(new BoxButton(left(), buttons, BUTTON_WIDTH, HEIGHT, CommonComponents.GUI_DONE, this::save));
        addRenderableWidget(new BoxButton(left() + BUTTON_WIDTH + 4, buttons, BUTTON_WIDTH, HEIGHT, CommonComponents.GUI_CANCEL, this::onClose));
        panelHeight = PAD_TOP + buttons + HEIGHT + PAD_BOTTOM;
        panelTop = Math.max(0, (height - panelHeight) / 2);
        top = panelTop + PAD_TOP;
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.setY(widget.getY() + top);
            }
        }
    }

    /**
     * Lays the rows out again on the next tick, once the click that changed them has finished. A widget's own click
     * handler must not rebuild the screen it's being clicked in.
     */
    protected void relayout() {
        rebuild = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuild) {
            rebuild = false;
            rebuildWidgets();
        }
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int fieldX() {
        return left() + LABEL_WIDTH;
    }

    private int fieldWidth() {
        return WIDTH - LABEL_WIDTH;
    }

    /** Starts a row {@code height} tall with {@code label} on the left. Returns its y, before the screen centres the rows. */
    private int row(Component label, int height) {
        int y = nextRow;
        nextRow += height + GAP;
        rowIndex = rowCount++;
        texts.add(new Text(left(), y + (height - 8) / 2, rowIndex, () -> label));
        return y;
    }

    /** Every widget is added through here, so it fades in with its row. */
    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        widgetRows.put(widget, rowIndex);
        return super.addRenderableWidget(widget);
    }

    /** How far its row's fade in has got, 0 to 1. */
    private float rowIn(int row) {
        return ROWS_IN.progress(UiClock.now() - openedAt, row);
    }

    /** A whole number, typed or stepped by {@code step} with the spinner corners of its box (ten steps with Shift held). */
    protected EditBox number(Component label, String value, Component hint, int step, Consumer<String> changed) {
        NumberBox box = new NumberBox(fieldX(), row(label, HEIGHT), fieldWidth(), HEIGHT, label, step);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(changed);
        return addRenderableWidget(box);
    }

    protected EditBox text(Component label, String value, int maxLength, Component hint, Consumer<String> changed) {
        BoxedEditBox box = new BoxedEditBox(fieldX(), row(label, HEIGHT), fieldWidth(), HEIGHT, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(changed);
        return addRenderableWidget(box);
    }

    protected <T> Dropdown<T> choice(Component label, List<T> values, T value, Function<T, Component> name, Consumer<T> changed) {
        return addRenderableWidget(new Dropdown<>(fieldX(), row(label, HEIGHT), fieldWidth(), HEIGHT, label, values, value, name, changed,
                dropdown -> open = dropdown));
    }

    protected Toggle toggle(Component label, boolean value, Consumer<Boolean> changed) {
        int y = row(label, HEIGHT);
        return addRenderableWidget(new Toggle(fieldX(), y + (HEIGHT - UiKit.SWITCH_SIZE) / 2, label, value, changed));
    }

    protected BoxButton button(Component label, Component text, Runnable pressed) {
        return addRenderableWidget(new BoxButton(fieldX(), row(label, HEIGHT), fieldWidth(), HEIGHT, text, pressed));
    }

    /** A row showing an item in a slot, with its name beside it. */
    protected void item(Component label, Supplier<ItemStack> stack, Supplier<Component> name) {
        int y = row(label, SLOT);
        slots.add(new Slot(fieldX(), y, rowIndex, stack));
        texts.add(new Text(fieldX() + SLOT + 4, y + (SLOT - 8) / 2, rowIndex, name));
    }

    /** A row showing a value that changes as the player edits. */
    protected void text(Component label, Supplier<Component> value) {
        int y = row(label, HEIGHT);
        texts.add(new Text(fieldX(), y + (HEIGHT - 8) / 2, rowIndex, value));
    }

    private void save() {
        DataResult<C> settings = settings();
        Optional<String> invalid = settings.error().map(e -> e.message());
        if (invalid.isPresent()) {
            showProblem(invalid.get());
            return;
        }
        CompoundTag tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, settings.result().orElseThrow()).getOrThrow(false, e -> {});
        PapNetwork.CHANNEL.sendToServer(new SaveSettingsPacket(pos, tag));
    }

    void showProblem(String message) {
        problem = Optional.of(Component.literal(message));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.tint(graphics, width, height);
        UiKit.frame(graphics, left() - PAD_SIDE, panelTop, WIDTH + 2 * PAD_SIDE, panelHeight);
        WrappedText.drawCentered(graphics, font, title, width / 2, panelTop + TITLE_Y, WIDTH + 2 * PAD_SIDE - TITLE_CLEARANCE, UiKit.TEXT);
        // Each row is drawn under its own alpha, which fades its boxes, sprites and text together.
        for (Slot slot : slots) {
            float in = rowIn(slot.row());
            if (in > 0) {
                graphics.setColor(1, 1, 1, in);
                UiKit.box(graphics, slot.x(), slot.y() + top, SLOT, SLOT, false, UiKit.Look.NORMAL);
                graphics.setColor(1, 1, 1, 1);
                // An item draws opaque whatever the alpha, so it comes in once its row is half there.
                if (in >= 0.5f) {
                    graphics.renderItem(slot.stack().get(), slot.x() + 2, slot.y() + top + 2);
                }
            }
        }
        for (Text text : texts) {
            float in = rowIn(text.row());
            if (in > 0) {
                graphics.setColor(1, 1, 1, in);
                graphics.drawString(font, text.text().get(), text.x(), text.y() + top, UiKit.TEXT);
                graphics.setColor(1, 1, 1, 1);
            }
        }
        boolean listOpen = open != null;
        for (Renderable widget : renderables) {
            float in = rowIn(widgetRows.getOrDefault(widget, rowCount));
            if (in > 0) {
                graphics.setColor(1, 1, 1, in);
                widget.render(graphics, listOpen ? -1 : mouseX, listOpen ? -1 : mouseY, partialTick);
                graphics.setColor(1, 1, 1, 1);
            }
        }
        float buttonsIn = rowIn(rowCount);
        if (buttonsIn > 0) {
            graphics.setColor(1, 1, 1, buttonsIn);
            problem.ifPresent(p -> WrappedText.drawCentered(graphics, font, p, width / 2, top + problemY, width, PROBLEM_COLOR));
            graphics.setColor(1, 1, 1, 1);
        }
        if (listOpen) {
            open.renderList(graphics, mouseX, mouseY, height);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (open != null) {
            Dropdown<?> list = open;
            open = null;
            list.clickList(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (open != null) {
            open.scrollList(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (open != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected static DataResult<Integer> cost(String text, String what) {
        return text.isEmpty() ? DataResult.error(() -> what + " needs a number") : DataResult.success(Integer.parseInt(text));
    }

    /** A blank cost means the machine uses the game's default. */
    protected static Optional<Integer> optionalCost(String text) {
        return text.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(text));
    }
}
