package dev.candycanecazoo.hellstorm.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A message with its key details (ids, names, numbers, positions, commands) marked to show in gold. A marked detail sits
 * between {@link #OPEN} and {@link #CLOSE}, so the message stays one plain string through codecs and packets, such as a
 * validation problem's.
 */
public final class Highlight {
    static final char OPEN = '«';
    static final char CLOSE = '»';

    private Highlight() {}

    /** {@code template} with each {@code {}} replaced by the next of {@code details}, marked. */
    public static String of(String template, Object... details) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        int from = 0;
        for (int at = template.indexOf("{}"); at >= 0; at = template.indexOf("{}", from)) {
            if (next >= details.length) {
                throw new IllegalArgumentException("More {} than details in: " + template);
            }
            out.append(template, from, at).append(key(details[next++]));
            from = at + 2;
        }
        if (next < details.length) {
            throw new IllegalArgumentException("More details than {} in: " + template);
        }
        return out.append(template.substring(from)).toString();
    }

    /** One detail, marked. A position reads {@code (x, y, z)}. */
    public static String key(Object detail) {
        String text = detail instanceof BlockPos pos ? "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                : detail instanceof Component component ? component.getString() : String.valueOf(detail);
        return OPEN + text + CLOSE;
    }

    /** The message as plain text, unmarked. */
    public static String plain(String message) {
        return message.replace(String.valueOf(OPEN), "").replace(String.valueOf(CLOSE), "");
    }

    /** The message in {@code color}, its marked details in gold. */
    public static MutableComponent component(String message, ChatFormatting color) {
        MutableComponent out = Component.empty();
        int from = 0;
        while (from < message.length()) {
            int open = message.indexOf(OPEN, from);
            int close = open < 0 ? -1 : message.indexOf(CLOSE, open);
            if (close < 0) {
                out.append(Component.literal(message.substring(from)).withStyle(color));
                break;
            }
            if (open > from) {
                out.append(Component.literal(message.substring(from, open)).withStyle(color));
            }
            out.append(Component.literal(message.substring(open + 1, close)).withStyle(ChatFormatting.GOLD));
            from = close + 1;
        }
        return out;
    }
}
