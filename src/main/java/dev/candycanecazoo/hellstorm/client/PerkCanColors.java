package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.perk.PerkCanItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A perk can's item color, which its GeckoLib model doesn't use: it's there for {@link VendingMachineSkins} to paint the
 * machine selling it, as a potion's color does.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PerkCanColors {
    private PerkCanColors() {}

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Item event) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof PerkCanItem can) {
                event.register((stack, tintIndex) -> tintIndex == 0 ? can.color(stack) : -1, can);
            }
        }
    }
}
