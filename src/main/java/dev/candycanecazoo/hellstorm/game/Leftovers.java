package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In a game, what drinking or eating leaves behind (a potion's bottle, a stew's bowl) is gone. The same items got any
 * other way stay.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class Leftovers {
    /**
     * Each player's inventory on the tick they finish using an item, just before. A drink from a stack of several keeps the
     * stack in hand and puts its leftover straight into the inventory, so that leftover is only seen as what was added.
     */
    private static final Map<UUID, Map<Item, Integer>> BEFORE = new HashMap<>();

    private Leftovers() {}

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getDuration() == 1 && event.getEntity() instanceof ServerPlayer player && GameManager.playing(player.getUUID()).isPresent()) {
            BEFORE.put(player.getUUID(), counts(player.getInventory()));
        }
    }

    @SubscribeEvent
    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Map<Item, Integer> before = BEFORE.remove(player.getUUID());
        if (GameManager.playing(player.getUUID()).isEmpty()) {
            return;
        }
        Item used = event.getItem().getItem();
        if (!event.getResultStack().isEmpty() && !event.getResultStack().is(used)) {
            event.setResultStack(ItemStack.EMPTY);
        }
        if (before == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        counts(inventory).forEach((item, count) -> {
            int added = count - before.getOrDefault(item, 0);
            if (item != used && added > 0) {
                inventory.clearOrCountMatchingItems(stack -> stack.is(item), added, player.inventoryMenu.getCraftSlots());
            }
        });
    }

    private static Map<Item, Integer> counts(Inventory inventory) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }
}
