package dev.candycanecazoo.packapunch.data;

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
import java.util.Optional;
import java.util.TreeMap;

/** Mystery Box pools from every datapack, refreshed on /reload. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class BoxPools {
    private static volatile Map<ResourceLocation, BoxPool> pools = Map.of();

    private BoxPools() {}

    public static Map<ResourceLocation, BoxPool> all() {
        return pools;
    }

    /** The built-in default (every TacZ gun, no wall-buys) stands in when no datapack defines {@code packapunch:default}. */
    public static Optional<BoxPool> get(ResourceLocation id) {
        BoxPool pool = pools.get(id);
        if (pool == null && id.equals(BoxPool.DEFAULT_ID)) {
            pool = BoxPool.DEFAULT;
        }
        return Optional.ofNullable(pool);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), PackAPunch.MOD_ID + "/box_pools") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, BoxPool> loaded = new TreeMap<>();
                files.forEach((id, json) -> BoxPool.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> PackAPunch.LOG.error("Bad box pool {}: {}", id, error))
                        .ifPresent(pool -> loaded.put(id, pool)));
                pools = Map.copyOf(loaded);
                PackAPunch.LOG.info("Loaded {} Pack-a-Punch box pools", loaded.size());
            }
        });
    }
}
