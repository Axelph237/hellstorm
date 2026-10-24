package dev.candycanecazoo.hellstorm.wave;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.util.DataFiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** Wave events from every datapack, refreshed on /reload. A game takes them when it starts. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class WaveEventDefs {
    private static volatile Map<ResourceLocation, WaveEventDef> events = Map.of();

    private WaveEventDefs() {}

    public static Map<ResourceLocation, WaveEventDef> all() {
        return events;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/wave_events") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, WaveEventDef> loaded = DataFiles.parse("wave event", WaveEventDef.CODEC, files);
                events = Map.copyOf(loaded);
                Hellstorm.LOG.info("Loaded {} Hellstorm wave events", loaded.size());
            }
        });
    }
}
