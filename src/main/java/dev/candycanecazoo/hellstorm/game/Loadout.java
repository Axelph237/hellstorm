package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
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
                    () -> Hellstorm.LOG.warn("Starting gun {} is not an installed TacZ gun; skipped", gun));
        }
        for (ResourceLocation id : settings.startingItems()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                Hellstorm.LOG.warn("Starting item {} does not exist; skipped", id);
            } else {
                player.getInventory().add(new ItemStack(item));
            }
        }
        player.inventoryMenu.broadcastChanges();
    }
}
