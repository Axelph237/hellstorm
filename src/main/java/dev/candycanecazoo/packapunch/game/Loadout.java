package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Clears a joining player's inventory and gives the starting guns and items. Unknown ids are logged and skipped. Starting guns
 * are punchable, like the CoD starting pistol.
 */
public final class Loadout {
    private Loadout() {}

    public static void give(ServerPlayer player, GameSettings.Players settings) {
        player.getInventory().clearContent();
        player.removeAllEffects();
        for (ResourceLocation gun : settings.startingGuns()) {
            TaczBridge.createGun(gun, settings.reserveMagazines()).ifPresentOrElse(
                    stack -> {
                        TaczBridge.setUnlimitedAmmo(stack, settings.startingGunsUnlimitedAmmo());
                        stack.getOrCreateTagElement(ItemSpec.PAP).putBoolean(ItemSpec.PUNCHABLE, true);
                        player.getInventory().add(stack);
                    },
                    () -> PackAPunch.LOG.warn("Starting gun {} is not an installed TacZ gun; skipped", gun));
        }
        for (ResourceLocation id : settings.startingItems()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                PackAPunch.LOG.warn("Starting item {} does not exist; skipped", id);
            } else {
                player.getInventory().add(new ItemStack(item));
            }
        }
        player.inventoryMenu.broadcastChanges();
    }
}
