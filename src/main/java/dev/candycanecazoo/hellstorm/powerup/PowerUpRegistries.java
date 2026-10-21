package dev.candycanecazoo.hellstorm.powerup;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PowerUpRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Hellstorm.MOD_ID);

    public static final RegistryObject<EntityType<PowerUpEntity>> POWER_UP = ENTITIES.register("power_up",
            () -> EntityType.Builder.<PowerUpEntity>of(PowerUpEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(8).updateInterval(2).fireImmune().noSummon()
                    .build(Hellstorm.id("power_up").toString()));

    private PowerUpRegistries() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
