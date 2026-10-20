package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.Viewer;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Block entity renderers for machines, and the local player for prompts that depend on the player's inventory. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FeatureRenderers {
    private FeatureRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PapRegistries.WALL_BUY_ENTITY.get(), WallBuyRenderer::new);
        event.registerBlockEntityRenderer(PapRegistries.MYSTERY_BOX_ENTITY.get(), MysteryBoxRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        Viewer.set(() -> Minecraft.getInstance().player);
    }
}
