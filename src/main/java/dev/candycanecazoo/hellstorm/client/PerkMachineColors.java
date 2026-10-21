package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tints a perk machine's body (tintindex 0) with the color of the potion it sells, or not at all for any other item. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PerkMachineColors {
    private static final int UNTINTED = 0xFFFFFF;

    private PerkMachineColors() {}

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 && level != null && pos != null
                && level.getBlockEntity(pos) instanceof WallBuyBlock.Entity machine ? color(machine.display()) : UNTINTED,
                PapRegistries.PERK_MACHINE.get());
    }

    static int color(ItemStack sold) {
        return PotionUtils.getMobEffects(sold).isEmpty() ? UNTINTED : PotionUtils.getColor(sold);
    }
}
