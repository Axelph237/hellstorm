package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.game.GameWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The team's timed power-ups in a row: each one's item, then "Insta-Kill 23", counting down in seconds. */
public final class PowerUpTimers extends HudElement {
    private static final int GAP = 12;
    /** A timer's item icon, and the space between it and the count. */
    private static final int ICON = 16;
    private static final int ICON_GAP = 2;
    private static final int ENDING_TICKS = 100;
    /** Each timer's icon, built once per item rather than every frame. */
    private static final Map<ItemSpec.Plain, ItemStack> ICONS = new HashMap<>();

    PowerUpTimers() {
        super("timers", "The timed power-ups: Insta-Kill, Double Points, Fire Sale.", 0.5, 0);
    }

    @Override
    protected Size size(Frame frame) {
        List<String> labels = labels(frame);
        if (labels.isEmpty()) {
            return Size.NONE;
        }
        int width = labels.stream().mapToInt(frame.font()::width).sum() + (ICON + ICON_GAP) * labels.size() + GAP * (labels.size() - 1);
        return new Size(width, ICON);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        List<String> labels = labels(frame);
        int text = top + (ICON - frame.font().lineHeight) / 2;
        int x = left;
        for (int i = 0; i < labels.size(); i++) {
            GameWorld.Timer timer = frame.hud().timers().get(i);
            boolean ending = timer.ticksLeft() < ENDING_TICKS && (timer.ticksLeft() / 5) % 2 == 0;
            frame.graphics().renderItem(ICONS.computeIfAbsent(timer.icon(), icon -> icon.build(0).orElse(ItemStack.EMPTY)), x, top);
            x += ICON + ICON_GAP;
            frame.graphics().drawString(frame.font(), labels.get(i), x, text, ending ? Colors.GREY : Colors.GOLD);
            x += frame.font().width(labels.get(i)) + GAP;
        }
    }

    private static List<String> labels(Frame frame) {
        return frame.hud().timers().stream()
                .map(t -> Component.translatable(t.name()).getString() + " " + (t.ticksLeft() + 19) / 20).toList();
    }
}
