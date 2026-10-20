package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.client.WrappedText;
import dev.candycanecazoo.packapunch.feature.ConfigBlockEntity;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.net.SaveSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A machine's settings as a column of labelled rows with Done and Cancel. Subclasses keep the values being edited in
 * fields and lay out their rows in {@link #rows()}, which runs again whenever the screen is rebuilt or {@link #relayout()}
 * is called. Done sends the
 * settings to the server, which checks them and either closes the screen or sends back the problem.
 */
abstract class SettingsScreen<C> extends Screen {
    private static final int WIDTH = 240;
    private static final int LABEL_WIDTH = 96;
    private static final int ROW = 21;
    private static final int HEIGHT = 20;
    private static final int MIN_TOP = 24;
    private static final int LABEL_COLOR = 0xE0E0E0;
    private static final int PROBLEM_COLOR = 0xFF5555;

    private final BlockPos pos;
    private final Codec<C> codec;
    private final List<Text> texts = new ArrayList<>();
    private int rows;
    private int top;
    private Optional<Component> problem = Optional.empty();
    private boolean rebuild;

    private record Text(int x, int y, Supplier<Component> text) {}

    protected SettingsScreen(Component title, ConfigBlockEntity<C> entity) {
        super(title);
        this.pos = entity.getBlockPos();
        this.codec = entity.codec();
    }

    BlockPos pos() {
        return pos;
    }

    /** Adds this screen's rows with {@link #number}, {@link #choice}, {@link #button}, and {@link #text}. */
    protected abstract void rows();

    /** The settings to send, or what's wrong with what was typed. */
    protected abstract DataResult<C> settings();

    @Override
    protected final void init() {
        texts.clear();
        rows = 0;
        rows();
        int buttons = rows * ROW + 6;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(left(), buttons, WIDTH / 2 - 2, HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left() + WIDTH / 2 + 2, buttons, WIDTH / 2 - 2, HEIGHT).build());
        top = Math.max(MIN_TOP, (height - buttons - HEIGHT) / 2);
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

    /** Starts a row with {@code label} on the left. Returns its y, before the screen centres the rows. */
    private int row(Component label) {
        int y = rows++ * ROW;
        texts.add(new Text(left(), y + 6, () -> label));
        return y;
    }

    protected EditBox number(Component label, String value, Component hint, Consumer<String> changed) {
        EditBox box = new EditBox(font, fieldX(), row(label), fieldWidth(), HEIGHT, label);
        box.setMaxLength(9);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(changed);
        return addRenderableWidget(box);
    }

    protected EditBox text(Component label, String value, int maxLength, Component hint, Consumer<String> changed) {
        EditBox box = new EditBox(font, fieldX(), row(label), fieldWidth(), HEIGHT, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(changed);
        return addRenderableWidget(box);
    }

    protected <T> CycleButton<T> choice(Component label, List<T> values, T value, Function<T, Component> name, Consumer<T> changed) {
        return addRenderableWidget(CycleButton.builder(name).withValues(values).withInitialValue(value).displayOnlyValue()
                .create(fieldX(), row(label), fieldWidth(), HEIGHT, label, (button, chosen) -> changed.accept(chosen)));
    }

    protected Button button(Component label, Component text, Runnable pressed) {
        return addRenderableWidget(Button.builder(text, b -> pressed.run()).bounds(fieldX(), row(label), fieldWidth(), HEIGHT).build());
    }

    /** A row showing a value that changes as the player edits, such as the item a wall-buy sells. */
    protected void text(Component label, Supplier<Component> value) {
        int y = row(label);
        texts.add(new Text(fieldX(), y + 6, value));
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
        renderBackground(graphics);
        WrappedText.drawCentered(graphics, font, title, width / 2, top - 16, width, 0xFFFFFF);
        for (Text text : texts) {
            graphics.drawString(font, text.text().get(), text.x(), text.y() + top, LABEL_COLOR);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        problem.ifPresent(p -> WrappedText.drawCentered(graphics, font, p, width / 2, top + rows * ROW + 6 + HEIGHT + 6, width, PROBLEM_COLOR));
    }

    protected static DataResult<Integer> cost(String text, String what) {
        return text.isEmpty() ? DataResult.error(() -> what + " needs a number") : DataResult.success(Integer.parseInt(text));
    }

    /** A blank cost means the machine uses the game's default. */
    protected static Optional<Integer> optionalCost(String text) {
        return text.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(text));
    }
}
