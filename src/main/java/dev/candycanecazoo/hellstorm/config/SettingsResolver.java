package dev.candycanecazoo.hellstorm.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves the settings for one game: the server config (whose defaults are {@link GameSettings#DEFAULT}),
 * with an optional per-map JSON override merged on top. The override may be partial: any value it names wins,
 * everything else keeps the server value.
 */
public final class SettingsResolver {
    private SettingsResolver() {}

    public static DataResult<GameSettings> resolve(GameSettings server, Optional<JsonElement> mapOverride) {
        if (mapOverride.isEmpty()) {
            return DataResult.success(server);
        }
        return GameSettings.CODEC.encodeStart(JsonOps.INSTANCE, server)
                .flatMap(base -> GameSettings.CODEC.parse(JsonOps.INSTANCE, merge(base.deepCopy(), mapOverride.get())));
    }

    /** Objects merge key by key. Anything else (numbers, strings, lists) replaces the base value. */
    static JsonElement merge(JsonElement base, JsonElement override) {
        if (!base.isJsonObject() || !override.isJsonObject()) {
            return override.deepCopy();
        }
        JsonObject merged = base.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : override.getAsJsonObject().entrySet()) {
            JsonElement existing = merged.get(entry.getKey());
            merged.add(entry.getKey(), existing == null ? entry.getValue().deepCopy() : merge(existing, entry.getValue()));
        }
        return merged;
    }
}
