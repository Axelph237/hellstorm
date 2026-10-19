package dev.candycanecazoo.packapunch.registry;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.feature.BarricadeBlock;
import dev.candycanecazoo.packapunch.feature.SpawnMarkerBlock;
import dev.candycanecazoo.packapunch.tools.ZoneWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PapRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PackAPunch.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PackAPunch.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PackAPunch.MOD_ID);

    public static final RegistryObject<Block> SPAWN_MARKER = BLOCKS.register("spawn_marker", () -> new SpawnMarkerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0f, 3_600_000f)
                    .noCollission().noOcclusion().noLootTable().sound(SoundType.METAL)));

    public static final RegistryObject<Block> BARRICADE = BLOCKS.register("barricade", () -> new BarricadeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3_600_000f)
                    .noOcclusion().noLootTable().sound(SoundType.WOOD).isViewBlocking((s, l, p) -> false)
                    // Vanilla's in-wall check ignores per-entity collision, so without this zombies climbing through suffocate.
                    .isSuffocating((s, l, p) -> false)));

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand", () -> new ZoneWandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPAWN_MARKER_ITEM = ITEMS.register("spawn_marker", () -> new BlockItem(SPAWN_MARKER.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.packapunch"))
            .icon(() -> ZONE_WAND.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(ZONE_WAND.get());
                output.accept(SPAWN_MARKER_ITEM.get());
            })
            .build());

    private PapRegistries() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
