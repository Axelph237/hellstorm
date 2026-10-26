package dev.candycanecazoo.hellstorm.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.util.DataFiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;

/**
 * Purchase defaults from every datapack, refreshed on /reload. A file's id is what it prices, as with gun balance:
 * {@code data/tacz/hellstorm/gun_purchases/ak47.json} is the gun {@code tacz:ak47}, and
 * {@code data/hellstorm/hellstorm/item_purchases/vigoraid.json} the item {@code hellstorm:vigoraid}. The mod's own files
 * price TacZ's default guns and the perk cans.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PurchaseDefaults {
    private static volatile Map<ResourceLocation, PurchaseDefault> guns = Map.of();
    private static volatile Map<ResourceLocation, PurchaseDefault> items = Map.of();

    private PurchaseDefaults() {}

    public static Optional<PurchaseDefault> of(ItemSpec item) {
        if (item instanceof ItemSpec.Gun gun) {
            return Optional.ofNullable(guns.get(gun.gunId()));
        }
        return item instanceof ItemSpec.Plain plain ? Optional.ofNullable(items.get(plain.item())) : Optional.empty();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/gun_purchases") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                guns = DataFiles.parse("gun purchase", PurchaseDefault.CODEC, files);
                Hellstorm.LOG.info("Loaded purchase defaults for {} guns", guns.size());
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), Hellstorm.MOD_ID + "/item_purchases") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                items = DataFiles.parse("item purchase", PurchaseDefault.CODEC, files, PurchaseDefaults::itemProblem);
                Hellstorm.LOG.info("Loaded purchase defaults for {} items", items.size());
            }
        });
    }

    static Optional<String> itemProblem(PurchaseDefault purchase) {
        return purchase.repurchase() == WallBuy.Repurchase.REFILL_AMMO
                ? Optional.of("only guns refill ammo; use top_up, duplicate or deny")
                : Optional.empty();
    }
}
