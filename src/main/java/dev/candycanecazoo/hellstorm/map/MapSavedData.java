package dev.candycanecazoo.hellstorm.map;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.UnaryOperator;

/** Persists one level's {@link ZombiesMap}. Use {@link dev.candycanecazoo.hellstorm.tools.MapEdits} to change it so clients stay in sync. */
public final class MapSavedData extends SavedData {
    private static final String NAME = Hellstorm.MOD_ID + "_map";

    private ZombiesMap map = ZombiesMap.EMPTY;
    /** Worlds saved before modes existed load as play, so their games keep working. */
    private MapMode mode = MapMode.PLAY;
    private ZoneIndex index;

    public static MapSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MapSavedData::load, MapSavedData::new, NAME);
    }

    public ZombiesMap map() {
        return map;
    }

    public MapMode mode() {
        return mode;
    }

    /** Use {@link dev.candycanecazoo.hellstorm.tools.MapModes#set}, which also shows or hides the spawn markers. */
    public void setMode(MapMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            setDirty();
        }
    }

    public ZoneIndex index() {
        if (index == null) {
            index = ZoneIndex.of(map);
        }
        return index;
    }

    /** Applies an edit. Returns true if the map changed. */
    public boolean update(UnaryOperator<ZombiesMap> edit) {
        ZombiesMap next = edit.apply(map);
        if (next.equals(map)) {
            return false;
        }
        map = next;
        index = null;
        setDirty();
        return true;
    }

    private static MapSavedData load(CompoundTag tag) {
        MapSavedData data = new MapSavedData();
        data.map = ZombiesMap.CODEC.parse(NbtOps.INSTANCE, tag.get("map"))
                .resultOrPartial(error -> Hellstorm.LOG.error("Failed to load zombies map: {}", error))
                .orElse(ZombiesMap.EMPTY);
        data.mode = tag.getString("mode").equals(MapMode.EDIT.getSerializedName()) ? MapMode.EDIT : MapMode.PLAY;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ZombiesMap.CODEC.encodeStart(NbtOps.INSTANCE, map)
                .resultOrPartial(error -> Hellstorm.LOG.error("Failed to save zombies map: {}", error))
                .ifPresent(encoded -> tag.put("map", encoded));
        tag.putString("mode", mode.getSerializedName());
        return tag;
    }
}
