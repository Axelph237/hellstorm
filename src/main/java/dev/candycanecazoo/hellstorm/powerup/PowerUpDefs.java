package dev.candycanecazoo.hellstorm.powerup;

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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

/** Power-ups from every datapack, refreshed on /reload. A game takes its drop table when it starts. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PowerUpDefs {
    private static volatile Map<ResourceLocation, PowerUpDef> powerUps = Map.of();

    private PowerUpDefs() {}

    public static Map<ResourceLocation, PowerUpDef> all() {
        return powerUps;
    }

    public static Optional<PowerUpDef> get(ResourceLocation id) {
        return Optional.ofNullable(powerUps.get(id));
    }

    /** What a new game can drop, by weight. */
    public static PowerUpDrops.Table table() {
        return new PowerUpDrops.Table(powerUps.entrySet().stream().map(e -> new PowerUpDrops.Entry(e.getKey(), e.getValue().weight())).toList());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/powerups") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, PowerUpDef> loaded = DataFiles.parse("power-up", PowerUpDef.CODEC, files, PowerUpDefs::unknownItem);
                powerUps = Map.copyOf(loaded);
                Hellstorm.LOG.info("Loaded {} Hellstorm power-ups", loaded.size());
            }
        });
    }

    private static Optional<String> unknownItem(PowerUpDef def) {
        return ForgeRegistries.ITEMS.containsKey(def.item().item()) ? Optional.empty() : Optional.of("unknown item " + def.item().item());
    }
}
