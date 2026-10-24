package dev.candycanecazoo.hellstorm.data;

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

import java.util.Map;

/**
 * Gun balance from every datapack, refreshed on /reload. A file's id is the gun's: {@code data/tacz/hellstorm/gun_balance/m1911.json}
 * is {@code tacz:m1911}, so a datapack overrides a gun by shipping the same path. The mod's own files balance TacZ's
 * default guns ({@code tools/balance_guns.py} writes them); a gun with no file plays as TacZ made it.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class GunBalances {
    private static volatile Map<ResourceLocation, GunBalance> balances = Map.of();

    private GunBalances() {}

    public static GunBalance of(ResourceLocation gun) {
        return balances.getOrDefault(gun, GunBalance.NONE);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/gun_balance") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, GunBalance> loaded = DataFiles.parse("gun balance", GunBalance.CODEC, files);
                balances = Map.copyOf(loaded);
                Hellstorm.LOG.info("Loaded balance for {} guns", loaded.size());
            }
        });
    }
}
