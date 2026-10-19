package dev.candycanecazoo.packapunch.mob;

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

/** Mob profiles from every datapack, refreshed on /reload. Games copy the set when they open. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class MobProfiles {
    private static volatile Map<ResourceLocation, MobProfile> profiles = Map.of();

    private MobProfiles() {}

    /** Every loaded profile, or the built-in zombie if no datapack defines any. */
    public static Map<ResourceLocation, MobProfile> all() {
        return profiles.isEmpty() ? Map.of(MobProfile.FALLBACK_ID, MobProfile.FALLBACK) : profiles;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), PackAPunch.MOD_ID + "/mob_profiles") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, MobProfile> loaded = new TreeMap<>();
                files.forEach((id, json) -> MobProfile.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> PackAPunch.LOG.error("Bad mob profile {}: {}", id, error))
                        .ifPresent(profile -> loaded.put(id, profile)));
                profiles = Map.copyOf(loaded);
                PackAPunch.LOG.info("Loaded {} Pack-a-Punch mob profiles", loaded.size());
            }
        });
    }
}
