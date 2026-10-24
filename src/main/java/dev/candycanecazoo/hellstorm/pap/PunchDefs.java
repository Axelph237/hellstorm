package dev.candycanecazoo.hellstorm.pap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Every upgrade file in {@code data/<ns>/hellstorm/punch/}, merged and refreshed on /reload. A file is an object: each key
 * is a pap id naming a {@link PunchDef}, except {@code defaults}, which holds a {@link GunDefault} per TacZ gun class.
 * <pre>
 * { "sword_mk2": { "item-id": "minecraft:diamond_sword", "nbt": { ... } },
 *   "defaults": { "pistol": { "nbt": { ... }, "method": "merge" } } }
 * </pre>
 * A key with a colon is the pap id itself; one without is in the file's namespace. When two files name the same pap id or
 * gun class, the one from the datapack loaded later wins, and within one datapack the file whose name sorts later.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PunchDefs {
    public static final String DEFAULTS = "defaults";
    private static final FileToIdConverter FILES = FileToIdConverter.json(Hellstorm.MOD_ID + "/punch");

    /** One file's id ({@code <ns>:<path>}, without the folder and {@code .json}) and contents. */
    public record File(ResourceLocation id, JsonElement json) {}

    /** {@code errors} are the entries that were skipped, each naming its file and key. */
    public record Loaded(Map<ResourceLocation, PunchDef> defs, Map<String, GunDefault> defaults, List<String> errors) {
        public static final Loaded EMPTY = new Loaded(Map.of(), Map.of(), List.of());
    }

    private static volatile Loaded loaded = Loaded.EMPTY;

    private PunchDefs() {}

    public static Loaded loaded() {
        return loaded;
    }

    public static Map<ResourceLocation, PunchDef> all() {
        return loaded.defs();
    }

    public static Map<String, GunDefault> defaults() {
        return loaded.defaults();
    }

    /** {@code files} in load order: later files override earlier ones. A bad entry is skipped, and the rest of its file kept. */
    static Loaded parse(List<File> files) {
        Map<ResourceLocation, PunchDef> defs = new TreeMap<>();
        Map<String, GunDefault> defaults = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (File file : files) {
            if (!file.json().isJsonObject()) {
                errors.add(file.id() + ": not a JSON object");
                continue;
            }
            file.json().getAsJsonObject().entrySet().forEach(entry -> {
                String where = file.id() + ", \"" + entry.getKey() + "\": ";
                if (entry.getKey().equals(DEFAULTS)) {
                    if (!entry.getValue().isJsonObject()) {
                        errors.add(where + "not an object of gun classes");
                        return;
                    }
                    entry.getValue().getAsJsonObject().entrySet().forEach(gunClass -> load(GunDefault.CODEC, gunClass.getValue(),
                            file.id() + ", defaults \"" + gunClass.getKey() + "\": ", errors, value -> defaults.put(gunClass.getKey(), value)));
                    return;
                }
                Optional<ResourceLocation> id = papId(file.id().getNamespace(), entry.getKey());
                if (id.isEmpty()) {
                    errors.add(where + "not a valid pap id (lowercase letters, digits, and _ - . /, with an optional namespace:)");
                    return;
                }
                load(PunchDef.CODEC, entry.getValue(), where, errors, def -> defs.put(id.get(), def));
            });
        }
        return new Loaded(Map.copyOf(defs), Map.copyOf(defaults), List.copyOf(errors));
    }

    private static <A> void load(Codec<A> codec, JsonElement json, String where, List<String> errors, Consumer<A> keep) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, json);
        // Not resultOrPartial: a partial result would load the entry with the bad field's default.
        result.error().ifPresentOrElse(error -> errors.add(where + error.message()), () -> keep.accept(result.result().orElseThrow()));
    }

    /** {@code key} itself when it has a namespace, else {@code <fileNamespace>:<key>}. */
    static Optional<ResourceLocation> papId(String fileNamespace, String key) {
        return Optional.ofNullable(key.indexOf(':') >= 0 ? ResourceLocation.tryParse(key) : ResourceLocation.tryBuild(fileNamespace, key));
    }

    /** Lowest-priority datapack first, so a later datapack's entries win. */
    private static Loaded read(ResourceManager manager) {
        List<String> packs = manager.listPacks().map(PackResources::packId).toList();
        List<Map.Entry<ResourceLocation, Resource>> found = new ArrayList<>(FILES.listMatchingResources(manager).entrySet());
        found.sort(Comparator.<Map.Entry<ResourceLocation, Resource>>comparingInt(e -> packs.indexOf(e.getValue().sourcePackId()))
                .thenComparing(Map.Entry::getKey));
        List<File> files = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : found) {
            ResourceLocation id = FILES.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                files.add(new File(id, JsonParser.parseReader(reader)));
            } catch (IOException | JsonParseException e) {
                unreadable.add(id + ": not valid JSON: " + e.getMessage());
            }
        }
        Loaded parsed = parse(files);
        return new Loaded(parsed.defs(), parsed.defaults(), Stream.concat(unreadable.stream(), parsed.errors().stream()).toList());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Loaded>() {
            @Override
            protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
                return read(manager);
            }

            @Override
            protected void apply(Loaded result, ResourceManager manager, ProfilerFiller profiler) {
                loaded = result;
                Hellstorm.LOG.info("Loaded {} upgrade definitions and {} gun class defaults", result.defs().size(), result.defaults().size());
            }
        });
    }
}
