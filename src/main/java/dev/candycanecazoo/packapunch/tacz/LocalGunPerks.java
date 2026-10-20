package dev.candycanecazoo.packapunch.tacz;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.candycanecazoo.packapunch.client.ClientPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Client only. TacZ times shots on the client too, so the local player's guns need the same perk fire rate as the server's. */
public final class LocalGunPerks {
    private LocalGunPerks() {}

    static double rpmMultiplier(ItemStack gun) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem() == gun ? ClientPerks.rpmMultiplier() : 1;
    }

    public static void refresh() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
        }
    }
}
