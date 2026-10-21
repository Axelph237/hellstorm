package dev.candycanecazoo.hellstorm;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryManager;

import java.util.List;
import java.util.Map;

/**
 * Worlds saved before the mod was renamed from Pack-a-Punch carry {@code packapunch:} ids. Each is mapped to its
 * {@code hellstorm:} id, with the perks and the crate under their new names, so those worlds load intact. Safe to delete
 * once no such world is left.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class LegacyIds {
    private static final String OLD_NAMESPACE = "packapunch";
    private static final Map<String, String> RENAMED = Map.of(
            "juggernog", "vigorade",
            "speed_cola", "reload",
            "double_tap", "hellfire",
            "quick_revive", "saints_brew",
            "stamin_up", "jockey_energy",
            "mule_kick", "off_hand",
            "mystery_box", "mystery_crate");
    private static final List<ResourceKey<? extends Registry<?>>> REGISTRIES = List.of(Registries.BLOCK, Registries.ITEM,
            Registries.BLOCK_ENTITY_TYPE, Registries.MOB_EFFECT, Registries.POTION, Registries.ENTITY_TYPE, Registries.ATTRIBUTE);

    private LegacyIds() {}

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        REGISTRIES.forEach(registry -> remap(event, registry));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void remap(MissingMappingsEvent event, ResourceKey registry) {
        // The event's own registry holds only the world's saved ids while it loads; the live registry has the new ones.
        IForgeRegistry live = RegistryManager.ACTIVE.getRegistry(registry);
        for (Object raw : event.getMappings(registry, OLD_NAMESPACE)) {
            MissingMappingsEvent.Mapping mapping = (MissingMappingsEvent.Mapping) raw;
            String path = mapping.getKey().getPath();
            ResourceLocation renamed = Hellstorm.id(RENAMED.getOrDefault(path, path));
            // Blocks and items answer a missing id with air, so check it exists rather than trusting a null.
            if (live != null && live.containsKey(renamed)) {
                mapping.remap(live.getValue(renamed));
            } else {
                Hellstorm.LOG.warn("No {} to map the old {} to", renamed, mapping.getKey());
            }
        }
    }
}
