package dev.candycanecazoo.hellstorm.feature;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.BlockAlignedItemRenderer;
import dev.candycanecazoo.hellstorm.client.BoneTextures;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** A machine's item, drawn with the machine's own GeckoLib model, moved by {@code offset} pixels as the block draws it. */
public final class GeoBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String model;
    private final Vec3 offset;

    public GeoBlockItem(Block block, Properties properties, String model, Vec3 offset) {
        super(block, properties);
        this.model = model;
        this.offset = offset;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(BlockAlignedItemRenderer.extensions(() -> new BlockAlignedItemRenderer<>(
                new DefaultedBlockGeoModel<GeoBlockItem>(Hellstorm.id(model)), BoneTextures.of(model), offset)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
