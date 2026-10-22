package dev.candycanecazoo.hellstorm.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.feature.GeoBlockItem;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import dev.candycanecazoo.hellstorm.feature.PowerSwitchBlock;
import dev.candycanecazoo.hellstorm.feature.SpawnMarkerBlock;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.perk.PerkEffects;
import dev.candycanecazoo.hellstorm.tools.ZoneWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
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

public final class PapRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Hellstorm.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hellstorm.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Hellstorm.MOD_ID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Hellstorm.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hellstorm.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Hellstorm.MOD_ID);

    // The attributes are built here, not in their suppliers: mob effects register before attributes, and the perk effects
    // (perk/PerkEffects) need them when they are built.

    /**
     * How fast a player reloads TacZ guns, as a multiple of normal: 2 reloads in half the time. Every player has it at a
     * base of 1; Load-Up adds 1, and anything else (effects, items, commands) can modify it like any attribute.
     */
    public static final Attribute RELOAD_SPEED = new RangedAttribute("attribute.name.hellstorm.reload_speed", 1.0, 0.1, 10.0).setSyncable(true);

    /** How fast a player revives teammates, as a multiple of normal: 2 revives in half the time. Saint's Brew adds 1. */
    public static final Attribute REVIVE_SPEED = new RangedAttribute("attribute.name.hellstorm.revive_speed", 1.0, 0.1, 10.0).setSyncable(true);

    /** How fast a player's TacZ guns fire, as a multiple of their rounds per minute. Hellfire adds 0.33. */
    public static final Attribute FIRE_RATE = new RangedAttribute("attribute.name.hellstorm.fire_rate", 1.0, 0.1, 10.0).setSyncable(true);

    /** TacZ guns a player can carry on top of {@code players.weapon_slots}. Off-Hand adds 1. */
    public static final Attribute EXTRA_WEAPON_SLOTS = new RangedAttribute("attribute.name.hellstorm.extra_weapon_slots", 0.0, 0.0, 9.0).setSyncable(true);

    static {
        ATTRIBUTES.register("reload_speed", () -> RELOAD_SPEED);
        ATTRIBUTES.register("revive_speed", () -> REVIVE_SPEED);
        ATTRIBUTES.register("fire_rate", () -> FIRE_RATE);
        ATTRIBUTES.register("extra_weapon_slots", () -> EXTRA_WEAPON_SLOTS);
    }

    public static final RegistryObject<Block> SPAWN_MARKER = BLOCKS.register("spawn_marker", () -> new SpawnMarkerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0f, 3_600_000f)
                    .noCollission().noOcclusion().noLootTable().sound(SoundType.METAL)));

    public static final RegistryObject<Block> BARRICADE = BLOCKS.register("barricade", () -> new BarricadeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3_600_000f)
                    .noOcclusion().noLootTable().sound(SoundType.WOOD).isViewBlocking((s, l, p) -> false)
                    // Vanilla's in-wall check ignores per-entity collision, so without this zombies climbing through suffocate.
                    .isSuffocating((s, l, p) -> false)));

    public static final RegistryObject<Block> POWER_SWITCH = BLOCKS.register("power_switch", () -> new PowerSwitchBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3_600_000f).noCollission().noLootTable().sound(SoundType.METAL)));
    public static final RegistryObject<BlockEntityType<PowerSwitchBlock.Entity>> POWER_SWITCH_ENTITY = BLOCK_ENTITIES.register("power_switch",
            () -> BlockEntityType.Builder.of(PowerSwitchBlock.Entity::new, POWER_SWITCH.get()).build(null));

    /** The vending machine keeps the perk machine's ids, which it replaced, so maps made with perk machines keep their settings. */
    public static final RegistryObject<Block> VENDING_MACHINE = BLOCKS.register("perk_machine", () -> new VendingMachineBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3_600_000f).noOcclusion().noLootTable().sound(SoundType.METAL)));
    public static final RegistryObject<BlockEntityType<VendingMachineBlock.Entity>> VENDING_MACHINE_ENTITY = BLOCK_ENTITIES.register("perk_machine",
            () -> BlockEntityType.Builder.of(VendingMachineBlock.Entity::new, VENDING_MACHINE.get()).build(null));
    public static final RegistryObject<SoundEvent> VENDING_MACHINE_IDLE = sound("vending_machine.idle");
    public static final RegistryObject<SoundEvent> VENDING_MACHINE_SHAKE = sound("vending_machine.shake");
    public static final RegistryObject<SoundEvent> VENDING_MACHINE_BUTTON = sound("vending_machine.button");
    /** Played to a player alone when they spend points or earn them rebuilding a board (see {@code Shop.chaChing}). */
    public static final RegistryObject<SoundEvent> CHA_CHING = sound("cha_ching");
    public static final RegistryObject<Block> WALL_BUY = BLOCKS.register("wall_buy", () -> new WallBuyBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3_600_000f).noOcclusion().noLootTable().sound(SoundType.WOOD)));
    public static final RegistryObject<BlockEntityType<WallBuyBlock.Entity>> WALL_BUY_ENTITY = BLOCK_ENTITIES.register("wall_buy",
            () -> BlockEntityType.Builder.of(WallBuyBlock.Entity::new, WALL_BUY.get()).build(null));

    public static final RegistryObject<Block> MYSTERY_BOX = BLOCKS.register("mystery_crate", () -> new MysteryBoxBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3_600_000f).noOcclusion().noLootTable().sound(SoundType.WOOD)));
    public static final RegistryObject<BlockEntityType<MysteryBoxBlock.Entity>> MYSTERY_BOX_ENTITY = BLOCK_ENTITIES.register("mystery_crate",
            () -> BlockEntityType.Builder.of(MysteryBoxBlock.Entity::new, MYSTERY_BOX.get()).build(null));
    public static final RegistryObject<Block> PAP_MACHINE = BLOCKS.register("pap_machine", () -> new PapMachineBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3_600_000f).noOcclusion().noLootTable()
                    .lightLevel(s -> 9).sound(SoundType.STONE)));
    public static final RegistryObject<BlockEntityType<PapMachineBlock.Entity>> PAP_MACHINE_ENTITY = BLOCK_ENTITIES.register("pap_machine",
            () -> BlockEntityType.Builder.of(PapMachineBlock.Entity::new, PAP_MACHINE.get()).build(null));

    public static final RegistryObject<Block> TRAP_CONTROLLER = BLOCKS.register("trap_controller", () -> new TrapControllerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3_600_000f).noLootTable().sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(TrapControllerBlock.POWERED) ? 9 : 0)));
    public static final RegistryObject<BlockEntityType<TrapControllerBlock.Entity>> TRAP_CONTROLLER_ENTITY = BLOCK_ENTITIES.register("trap_controller",
            () -> BlockEntityType.Builder.of(TrapControllerBlock.Entity::new, TRAP_CONTROLLER.get()).build(null));

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand", () -> new ZoneWandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPAWN_MARKER_ITEM = ITEMS.register("spawn_marker", () -> new BlockItem(SPAWN_MARKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_SWITCH_ITEM = ITEMS.register("power_switch", () -> new BlockItem(POWER_SWITCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> VENDING_MACHINE_ITEM = ITEMS.register("perk_machine", () -> new BlockItem(VENDING_MACHINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WALL_BUY_ITEM = ITEMS.register("wall_buy", () -> new BlockItem(WALL_BUY.get(), new Item.Properties()));
    public static final RegistryObject<Item> MYSTERY_BOX_ITEM = ITEMS.register("mystery_crate", () -> new GeoBlockItem(MYSTERY_BOX.get(), new Item.Properties(),
            MysteryBoxBlock.MODEL, MysteryBoxBlock.MODEL_OFFSET));
    public static final RegistryObject<Item> PAP_MACHINE_ITEM = ITEMS.register("pap_machine", () -> new BlockItem(PAP_MACHINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> TRAP_CONTROLLER_ITEM = ITEMS.register("trap_controller", () -> new BlockItem(TRAP_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hellstorm"))
            .icon(() -> ZONE_WAND.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(ZONE_WAND.get());
                output.accept(SPAWN_MARKER_ITEM.get());
                output.accept(POWER_SWITCH_ITEM.get());
                output.accept(VENDING_MACHINE_ITEM.get());
                output.accept(WALL_BUY_ITEM.get());
                output.accept(MYSTERY_BOX_ITEM.get());
                output.accept(PAP_MACHINE_ITEM.get());
                output.accept(TRAP_CONTROLLER_ITEM.get());
            })
            .build());

    private PapRegistries() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Hellstorm.id(name)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        SOUNDS.register(modBus);
        ATTRIBUTES.register(modBus);
        PerkEffects.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            event.add(EntityType.PLAYER, RELOAD_SPEED);
            event.add(EntityType.PLAYER, REVIVE_SPEED);
            event.add(EntityType.PLAYER, FIRE_RATE);
            event.add(EntityType.PLAYER, EXTRA_WEAPON_SLOTS);
        });
    }
}
