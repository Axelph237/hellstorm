package dev.candycanecazoo.hellstorm.client.gen;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.registry.GenRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterPresetEditorsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The client side of generated maps: the Room Block's outline, and the generated-map world's Customize screen. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GenClient {
    private GenClient() {}

    @SubscribeEvent
    public static void registerRoomBlockRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GenRegistries.ROOM_BLOCK_ENTITY.get(), RoomBlockRenderer::new);
    }

    @SubscribeEvent
    public static void registerPresetEditor(RegisterPresetEditorsEvent event) {
        event.register(ResourceKey.create(Registries.WORLD_PRESET, Hellstorm.id("zombies_map")), GeneratedMapPresetScreen::new);
    }
}
