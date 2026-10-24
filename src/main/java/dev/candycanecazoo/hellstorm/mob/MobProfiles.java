package dev.candycanecazoo.hellstorm.mob;

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

/** Mob profiles from every datapack, refreshed on /reload. Games copy the set when they open. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class MobProfiles {
    private static volatile Map<ResourceLocation, MobProfile> profiles = Map.of();

    private MobProfiles() {}

    /** Every loaded profile, or the built-in zombie if no datapack defines any. */
    public static Map<ResourceLocation, MobProfile> all() {
        return profiles.isEmpty() ? Map.of(MobProfile.FALLBACK_ID, MobProfile.FALLBACK) : profiles;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/mob_profiles") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, MobProfile> loaded = DataFiles.parse("mob profile", MobProfile.CODEC, files);
                profiles = Map.copyOf(loaded);
                Hellstorm.LOG.info("Loaded {} Hellstorm mob profiles", loaded.size());
            }
        });
    }
}
