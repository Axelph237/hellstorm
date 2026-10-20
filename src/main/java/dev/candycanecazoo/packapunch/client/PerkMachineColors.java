package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tints a perk machine's body (tintindex 0) with its perk's color. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PerkMachineColors {
    private static final int UNTINTED = 0xFFFFFF;

    private PerkMachineColors() {}

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 && level != null && pos != null
                && level.getBlockEntity(pos) instanceof PerkMachineBlock.Entity machine ? machine.color() : UNTINTED,
                PapRegistries.PERK_MACHINE.get());
    }
}
