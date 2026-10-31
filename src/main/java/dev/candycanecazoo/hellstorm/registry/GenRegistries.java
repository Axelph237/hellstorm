package dev.candycanecazoo.hellstorm.registry;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.gen.block.ConnectorBlock;
import dev.candycanecazoo.hellstorm.gen.block.PlaceholderBlock;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.gen.pack.PlaceholderKind;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The blocks for building rooms of generated maps: placeholders, connectors and the Room Block. */
public final class GenRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Hellstorm.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hellstorm.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Hellstorm.MOD_ID);

    private static BlockBehaviour.Properties marker() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5f).sound(SoundType.STONE).noLootTable();
    }

    public static final Map<PlaceholderKind, RegistryObject<Block>> PLACEHOLDERS = new EnumMap<>(PlaceholderKind.class);
    public static final Map<PlaceholderKind, RegistryObject<Item>> PLACEHOLDER_ITEMS = new EnumMap<>(PlaceholderKind.class);

    static {
        for (PlaceholderKind kind : PlaceholderKind.values()) {
            String id = "placeholder_" + kind.id();
            RegistryObject<Block> block = BLOCKS.register(id, () -> new PlaceholderBlock(kind, marker()));
            PLACEHOLDERS.put(kind, block);
            PLACEHOLDER_ITEMS.put(kind, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<Block> CONNECTOR = BLOCKS.register("connector", () -> new ConnectorBlock(marker()));
    public static final RegistryObject<Item> CONNECTOR_ITEM = ITEMS.register("connector", () -> new BlockItem(CONNECTOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ConnectorBlock.Entity>> CONNECTOR_ENTITY = BLOCK_ENTITIES.register("connector",
            () -> BlockEntityType.Builder.of(ConnectorBlock.Entity::new, CONNECTOR.get()).build(null));

    public static final RegistryObject<Block> ROOM_BLOCK = BLOCKS.register("room_block", () -> new RoomBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(-1.0f, 3_600_000f).noLootTable()));
    public static final RegistryObject<Item> ROOM_BLOCK_ITEM = ITEMS.register("room_block", () -> new BlockItem(ROOM_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<RoomBlock.Entity>> ROOM_BLOCK_ENTITY = BLOCK_ENTITIES.register("room_block",
            () -> BlockEntityType.Builder.of(RoomBlock.Entity::new, ROOM_BLOCK.get()).build(null));

    private GenRegistries() {}

    /** The map maker's kit, in the order the creative tab and the workshop hand them out. */
    public static List<Item> kit() {
        List<Item> kit = new java.util.ArrayList<>(List.of(ROOM_BLOCK_ITEM.get(), CONNECTOR_ITEM.get()));
        PLACEHOLDER_ITEMS.values().forEach(item -> kit.add(item.get()));
        return kit;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
