package dev.candycanecazoo.packapunch.perk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Perks from every datapack, refreshed on /reload. A game keeps each perk as it was when bought. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class PerkDefs {
    private static volatile Map<ResourceLocation, PerkDef> perks = Map.of();
    private static boolean warnedReloadSpeed;

    private PerkDefs() {}

    public static Map<ResourceLocation, PerkDef> all() {
        return perks;
    }

    public static Optional<PerkDef> get(ResourceLocation id) {
        return Optional.ofNullable(perks.get(id));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), PackAPunch.MOD_ID + "/perks") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, PerkDef> loaded = new TreeMap<>();
                files.forEach((id, json) -> PerkDef.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> PackAPunch.LOG.error("Bad perk {}: {}", id, error))
                        .filter(def -> knownAttributes(id, def))
                        .ifPresent(def -> loaded.put(id, def)));
                perks = Map.copyOf(loaded);
                PackAPunch.LOG.info("Loaded {} Pack-a-Punch perks", loaded.size());
                if (!warnedReloadSpeed && loaded.values().stream().anyMatch(d -> d.effects().stream().anyMatch(PerkEffect.ReloadSpeed.class::isInstance))) {
                    warnedReloadSpeed = true;
                    PackAPunch.LOG.warn("reload_speed perk effects do nothing yet: TacZ 1.1.8 has no per-player or per-gun reload time hook");
                }
            }
        });
    }

    private static boolean knownAttributes(ResourceLocation id, PerkDef def) {
        for (PerkEffect effect : def.effects()) {
            if (effect instanceof PerkEffect.Attribute a && !ForgeRegistries.ATTRIBUTES.containsKey(a.attribute())) {
                PackAPunch.LOG.error("Bad perk {}: unknown attribute {}", id, a.attribute());
                return false;
            }
        }
        return true;
    }

    /** Machines show their perk's name, color, and cost from synced data, so a /reload resends every loaded machine. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        for (ServerLevel level : event.getPlayerList().getServer().getAllLevels()) {
            for (BlockPos pos : MapSavedData.get(level).map().features(FeatureKind.PERK_MACHINE)) {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PerkMachineBlock.Entity machine) {
                    machine.refresh();
                }
            }
        }
    }
}
