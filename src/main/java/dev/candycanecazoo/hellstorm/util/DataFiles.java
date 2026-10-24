package dev.candycanecazoo.hellstorm.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Datapack JSON files read strictly: a file with any error is skipped whole and logged, rather than loaded with the bad
 * field's default (as {@code resultOrPartial} would, enforced by CodecLintTest). The last load's skipped files are kept
 * by kind for {@link #errors}.
 */
public final class DataFiles {
    private static final Map<String, List<String>> ERRORS = new ConcurrentHashMap<>();

    private DataFiles() {}

    /** Parses every file of {@code kind} ("mob profile") with {@code codec}, by id. */
    public static <A> Map<ResourceLocation, A> parse(String kind, Codec<A> codec, Map<ResourceLocation, JsonElement> files) {
        return parse(kind, codec, files, value -> Optional.empty());
    }

    /** As {@link #parse(String, Codec, Map)}, also skipping a file whose value {@code problem} refuses. */
    public static <A> Map<ResourceLocation, A> parse(String kind, Codec<A> codec, Map<ResourceLocation, JsonElement> files,
                                                     Function<A, Optional<String>> problem) {
        Map<ResourceLocation, A> loaded = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            DataResult<A> parsed = codec.parse(JsonOps.INSTANCE, json);
            Optional<String> error = parsed.error().map(DataResult.PartialResult::message).or(() -> problem.apply(parsed.result().orElseThrow()));
            if (error.isPresent()) {
                Hellstorm.LOG.error("Skipped {} {}: {}", kind, id, error.get());
                errors.add(id + ": " + error.get());
            } else {
                loaded.put(id, parsed.result().orElseThrow());
            }
        });
        ERRORS.put(kind, List.copyOf(errors));
        return Collections.unmodifiableMap(loaded);
    }

    /** The files the last load of each kind skipped, by kind, each as {@code "<id>: <why>"}. Kinds with none map to an empty list. */
    public static Map<String, List<String>> errors() {
        return Collections.unmodifiableMap(new TreeMap<>(ERRORS));
    }
}
