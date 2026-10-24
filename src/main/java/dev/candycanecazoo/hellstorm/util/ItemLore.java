package dev.candycanecazoo.hellstorm.util;

import dev.candycanecazoo.hellstorm.perk.PerkCanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** An item's lore lines, as its tooltip shows them: its {@code display.Lore} tag, or else a perk can's own blurb. */
public final class ItemLore {
    /** Vanilla's lore style, under whatever a line sets itself. */
    public static final Style STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);

    private ItemLore() {}

    public static List<Component> of(ItemStack stack) {
        List<Component> tagged = tagged(stack);
        if (!tagged.isEmpty()) {
            return tagged;
        }
        return stack.getItem() instanceof PerkCanItem can ? List.of(can.lore()) : List.of();
    }

    /** The {@code display.Lore} lines, each a JSON text component; a line that doesn't parse is left out. */
    public static List<Component> tagged(ItemStack stack) {
        CompoundTag display = stack.getTagElement(ItemStack.TAG_DISPLAY);
        if (display == null || !display.contains(ItemStack.TAG_LORE, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag lore = display.getList(ItemStack.TAG_LORE, Tag.TAG_STRING);
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < lore.size(); i++) {
            try {
                MutableComponent line = Component.Serializer.fromJson(lore.getString(i));
                if (line != null) {
                    lines.add(ComponentUtils.mergeStyles(line, STYLE));
                }
            } catch (RuntimeException e) {
                // As vanilla's tooltip does, a bad line is skipped.
            }
        }
        return lines;
    }
}
