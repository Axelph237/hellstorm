package dev.candycanecazoo.hellstorm.feature;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.BlockAlignedItemRenderer;
import dev.candycanecazoo.hellstorm.client.BoneTextures;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** The machine's item, drawn with the machine's own GeckoLib model. */
public final class PapMachineItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PapMachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(BlockAlignedItemRenderer.extensions(() -> new BlockAlignedItemRenderer<>(
                new DefaultedBlockGeoModel<PapMachineItem>(Hellstorm.id(PapMachineBlock.MODEL)), BoneTextures.HELLFIRE_UPGRADER)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
