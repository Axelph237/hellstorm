package dev.candycanecazoo.hellstorm.script;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The scripts and EventDefs from every loaded datapack, refreshed on /reload. Games copy them when their runtime is built,
 * so a reload takes effect from the next game (or /hs scripts reload).
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class ScriptRegistry {
    static final String SCRIPTS_DIR = Hellstorm.MOD_ID + "/scripts";
    static final String EVENTS_DIR = Hellstorm.MOD_ID + "/events";

    private static volatile Map<String, String> scripts = Map.of();
    private static volatile List<EventDef> eventDefs = List.of();

    private ScriptRegistry() {}

    public static Map<String, String> scripts() {
        return scripts;
    }

    public static List<EventDef> eventDefs() {
        return eventDefs;
    }

    /** {@code ns:hellstorm/scripts/a/b.lua} becomes the script id {@code ns:a/b}. */
    static Optional<String> scriptId(ResourceLocation file) {
        String path = file.getPath();
        if (!path.startsWith(SCRIPTS_DIR + "/") || !path.endsWith(".lua")) {
            return Optional.empty();
        }
        return Optional.of(file.getNamespace() + ":" + path.substring(SCRIPTS_DIR.length() + 1, path.length() - ".lua".length()));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ScriptLoader());
        event.addListener(new EventDefLoader());
    }

    private static final class ScriptLoader extends SimplePreparableReloadListener<Map<String, String>> {
        @Override
        protected Map<String, String> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, String> found = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(SCRIPTS_DIR, f -> f.getPath().endsWith(".lua")).entrySet()) {
                scriptId(entry.getKey()).ifPresent(id -> {
                    try (Reader reader = entry.getValue().openAsReader()) {
                        StringBuilder text = new StringBuilder();
                        char[] buffer = new char[4096];
                        for (int n; (n = reader.read(buffer)) > 0; ) {
                            text.append(buffer, 0, n);
                        }
                        found.put(id, text.toString());
                    } catch (IOException e) {
                        Hellstorm.LOG.error("Couldn't read script {}: {}", entry.getKey(), e.getMessage());
                    }
                });
            }
            return found;
        }

        @Override
        protected void apply(Map<String, String> loaded, ResourceManager manager, ProfilerFiller profiler) {
            scripts = Map.copyOf(loaded);
            Hellstorm.LOG.info("Loaded {} Hellstorm scripts", loaded.size());
        }
    }

    private static final class EventDefLoader extends SimpleJsonResourceReloadListener {
        EventDefLoader() {
            super(new Gson(), EVENTS_DIR);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<EventDef> defs = new ArrayList<>();
            files.forEach((id, json) -> EventDef.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> Hellstorm.LOG.error("Bad event def {}: {}", id, error))
                    .ifPresent(defs::add));
            eventDefs = List.copyOf(defs);
            Hellstorm.LOG.info("Loaded {} Hellstorm event defs", defs.size());
        }
    }
}
