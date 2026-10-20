package dev.candycanecazoo.packapunch.wave;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.TreeMap;

/** Wave events from every datapack, refreshed on /reload. A game takes them when it starts. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class WaveEventDefs {
    private static volatile Map<ResourceLocation, WaveEventDef> events = Map.of();

    private WaveEventDefs() {}

    public static Map<ResourceLocation, WaveEventDef> all() {
        return events;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), PackAPunch.MOD_ID + "/wave_events") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, WaveEventDef> loaded = new TreeMap<>();
                files.forEach((id, json) -> WaveEventDef.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> PackAPunch.LOG.error("Bad wave event {}: {}", id, error))
                        .ifPresent(def -> loaded.put(id, def)));
                events = Map.copyOf(loaded);
                PackAPunch.LOG.info("Loaded {} Pack-a-Punch wave events", loaded.size());
            }
        });
    }
}
