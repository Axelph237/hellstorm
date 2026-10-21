package dev.candycanecazoo.hellstorm.tacz;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client only. TacZ times shots on the client too, so the local player's held gun needs the same fire rate as the
 * server's. The attribute is synced; when it changes, the held gun is re-evaluated.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class LocalGunPerks {
    private static double lastFireRate = 1;

    private LocalGunPerks() {}

    static Player holder(ItemStack gun) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem() == gun ? player : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.phase != TickEvent.Phase.END || player == null) {
            return;
        }
        double fireRate = player.getAttributeValue(PapRegistries.FIRE_RATE);
        if (fireRate != lastFireRate) {
            lastFireRate = fireRate;
            AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
        }
    }
}
