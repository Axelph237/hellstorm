package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.perk.PerkCanItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Draws a perk can with GeckoLib's own item renderer, whose lift the GeckoLib Blockbench plugin's display preview assumes,
 * so {@code models/item/<id>.json} exported from there means what it showed. It's built here, in a client-only class, so
 * {@link PerkCanItem} never names a renderer type the server would have to load.
 */
public final class PerkCanRenderer {
    private PerkCanRenderer() {}

    public static IClientItemExtensions extensions(String model) {
        return BlockAlignedItemRenderer.lazy(() -> new GeoItemRenderer<PerkCanItem>(new DefaultedItemGeoModel<>(Hellstorm.id(model))));
    }
}
