package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clears a joining player's inventory and gives the starting guns and items. Unknown ids are logged and skipped. Starting guns
 * are punchable, like the CoD starting pistol.
 */
public final class Loadout {
    private Loadout() {}

    public static void give(ServerPlayer player, GameSettings.Players settings) {
        player.getInventory().clearContent();
        player.removeAllEffects();
        for (ItemSpec.Gun gun : settings.startingGuns()) {
            new ItemSpec.Gun(gun.gunId(), settings.startingGunsUnlimitedAmmo(), true, gun.nbt()).build(settings.reserveMagazines()).ifPresentOrElse(
                    player.getInventory()::add,
                    () -> Hellstorm.LOG.warn("Starting gun {} is not an installed TacZ gun; skipped", gun.gunId()));
        }
        for (ItemSpec.Plain item : settings.startingItems()) {
            item.build(0).ifPresentOrElse(player.getInventory()::add,
                    () -> Hellstorm.LOG.warn("Starting item {} does not exist; skipped", item.item()));
        }
        player.inventoryMenu.broadcastChanges();
    }
}
