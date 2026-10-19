package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player's normal state (inventory, effects, game mode, health, food, xp, position) saved on joining a game and
 * restored on leaving it. Stored on disk with the overworld, so a crash mid-game still gives everything back on the next login.
 */
public final class Sessions extends SavedData {
    private static final String NAME = PackAPunch.MOD_ID + "_sessions";

    private final Map<UUID, CompoundTag> snapshots = new HashMap<>();

    public static Sessions get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Sessions::load, Sessions::new, NAME);
    }

    public boolean has(UUID player) {
        return snapshots.containsKey(player);
    }

    /** Saves the player's state once. A second save while one exists is ignored, so the original is never overwritten. */
    public void save(ServerPlayer player) {
        if (snapshots.containsKey(player.getUUID())) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", player.getInventory().save(new ListTag()));
        ListTag effects = new ListTag();
        player.getActiveEffects().forEach(e -> effects.add(e.save(new CompoundTag())));
        tag.put("effects", effects);
        tag.putInt("game_mode", player.gameMode.getGameModeForPlayer().getId());
        tag.putFloat("health", player.getHealth());
        tag.putInt("food", player.getFoodData().getFoodLevel());
        tag.putFloat("saturation", player.getFoodData().getSaturationLevel());
        tag.putInt("xp_level", player.experienceLevel);
        tag.putFloat("xp_progress", player.experienceProgress);
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        snapshots.put(player.getUUID(), tag);
        setDirty();
    }

    /** Puts the player back exactly as saved and forgets the snapshot. Returns false if there was none. */
    public boolean restore(ServerPlayer player) {
        CompoundTag tag = snapshots.remove(player.getUUID());
        if (tag == null) {
            return false;
        }
        setDirty();
        player.getInventory().clearContent();
        player.getInventory().load(tag.getList("inventory", Tag.TAG_COMPOUND));
        player.removeAllEffects();
        ListTag effects = tag.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
            if (effect != null) {
                player.addEffect(effect);
            }
        }
        player.setGameMode(GameType.byId(tag.getInt("game_mode")));
        player.setHealth(tag.getFloat("health"));
        player.getFoodData().setFoodLevel(tag.getInt("food"));
        player.getFoodData().setSaturation(tag.getFloat("saturation"));
        player.experienceLevel = tag.getInt("xp_level");
        player.experienceProgress = tag.getFloat("xp_progress");
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        player.teleportTo(level != null ? level : player.server.getLevel(Level.OVERWORLD),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static Sessions load(CompoundTag tag) {
        Sessions sessions = new Sessions();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            sessions.snapshots.put(UUID.fromString(key), players.getCompound(key));
        }
        return sessions;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        snapshots.forEach((id, snapshot) -> players.put(id.toString(), snapshot));
        tag.put("players", players);
        return tag;
    }
}
