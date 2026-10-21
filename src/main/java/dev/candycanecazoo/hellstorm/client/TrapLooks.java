package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A trap with a look ({@link TrapControllerBlock.Entity#look}) is drawn as that block on every side, tinted as it would
 * be there. Without one it is drawn as itself.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrapLooks {
    private static final int UNTINTED = -1;

    private TrapLooks() {}

    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        for (BlockState state : PapRegistries.TRAP_CONTROLLER.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new Model(model));
        }
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
                        && level.getBlockEntity(pos) instanceof TrapControllerBlock.Entity trap && trap.look().isPresent()
                        ? Minecraft.getInstance().getBlockColors().getColor(trap.look().get(), level, pos, tintIndex)
                        : UNTINTED,
                PapRegistries.TRAP_CONTROLLER.get());
    }

    private static final class Model extends BakedModelWrapper<BakedModel> {
        Model(BakedModel own) {
            super(own);
        }

        private static BakedModel modelOf(BlockState look) {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(look);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                        @Nullable RenderType renderType) {
            BlockState look = data.get(TrapControllerBlock.Entity.LOOK_PROPERTY);
            if (look == null || state == null) {
                return super.getQuads(state, side, rand, data, renderType);
            }
            BakedModel lookModel = modelOf(look);
            return renderType == null || lookModel.getRenderTypes(look, rand, ModelData.EMPTY).contains(renderType)
                    ? lookModel.getQuads(look, side, rand, ModelData.EMPTY, renderType)
                    : List.of();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            BlockState look = data.get(TrapControllerBlock.Entity.LOOK_PROPERTY);
            return look == null ? super.getRenderTypes(state, rand, data) : modelOf(look).getRenderTypes(look, rand, ModelData.EMPTY);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            BlockState look = data.get(TrapControllerBlock.Entity.LOOK_PROPERTY);
            return look == null ? super.getParticleIcon(data) : modelOf(look).getParticleIcon(ModelData.EMPTY);
        }
    }
}
