package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.Viewer;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Block entity renderers for machines, and the local player for prompts that depend on the player's inventory. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FeatureRenderers {
    private FeatureRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PapRegistries.WALL_BUY_ENTITY.get(), WallBuyRenderer::new);
        event.registerBlockEntityRenderer(PapRegistries.PERK_MACHINE_ENTITY.get(), WallBuyRenderer::new);
        event.registerBlockEntityRenderer(PapRegistries.MYSTERY_BOX_ENTITY.get(), MysteryBoxRenderer::new);
        event.registerBlockEntityRenderer(PapRegistries.PAP_MACHINE_ENTITY.get(), PapMachineRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        Viewer.set(() -> Minecraft.getInstance().player);
    }
}
