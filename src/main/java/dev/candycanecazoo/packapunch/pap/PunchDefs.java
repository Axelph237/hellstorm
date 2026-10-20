package dev.candycanecazoo.packapunch.pap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
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

/** Every {@link PunchDef} from every datapack, refreshed on /reload. A datapack overrides a default by reusing its id. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class PunchDefs {
    private static volatile Map<ResourceLocation, PunchDef> defs = Map.of();

    private PunchDefs() {}

    public static Map<ResourceLocation, PunchDef> all() {
        return defs;
    }

    /** Bad files are logged and skipped. */
    static Map<ResourceLocation, PunchDef> parse(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, PunchDef> loaded = new TreeMap<>();
        files.forEach((id, json) -> {
            DataResult<PunchDef> result = PunchDef.CODEC.parse(JsonOps.INSTANCE, json);
            // Not resultOrPartial: a partial result would load the def with the bad field's default.
            result.error().ifPresentOrElse(
                    error -> PackAPunch.LOG.error("Bad Pack-a-Punch definition {}: {}", id, error.message()),
                    () -> loaded.put(id, result.result().orElseThrow()));
        });
        return Map.copyOf(loaded);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), PackAPunch.MOD_ID + "/punch") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                defs = parse(files);
                PackAPunch.LOG.info("Loaded {} Pack-a-Punch definitions", defs.size());
            }
        });
    }
}
