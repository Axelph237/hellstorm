package dev.candycanecazoo.hellstorm.perk;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.client.PerkEffectIcon;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import dev.candycanecazoo.hellstorm.tacz.PerkGunEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * The default perks as mob effects, each a single attribute modifier so anything else can stack with it, and a potion of
 * each that gives the effect with no time limit. What makes an effect a perk is the {@code hellstorm:perks} tag.
 */
public final class PerkEffects {
    public static final TagKey<MobEffect> PERKS = TagKey.create(Registries.MOB_EFFECT, Hellstorm.id("perks"));
    /** A solo player who goes down with one of these revives themselves (see downs-and-revives.md). */
    public static final TagKey<MobEffect> SELF_REVIVE = TagKey.create(Registries.MOB_EFFECT, Hellstorm.id("self_revive"));

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Hellstorm.MOD_ID);
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, Hellstorm.MOD_ID);

    public static final RegistryObject<MobEffect> VIGORADE = perk("vigorade", 0, 0xd33a2c,
            Attributes.MAX_HEALTH, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e01", 20, AttributeModifier.Operation.ADDITION);
    public static final RegistryObject<MobEffect> RELOAD = perk("reload", 5, 0x3fae4a,
            PapRegistries.RELOAD_SPEED, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e02", 1, AttributeModifier.Operation.ADDITION);
    public static final RegistryObject<MobEffect> HELLFIRE = perk("hellfire", 1, 0xe3a92b,
            PapRegistries.FIRE_RATE, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e03", 0.33, AttributeModifier.Operation.ADDITION);
    public static final RegistryObject<MobEffect> SAINTS_BREW = perk("saints_brew", 2, 0xd6a44f,
            PapRegistries.REVIVE_SPEED, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e04", 1, AttributeModifier.Operation.ADDITION);
    public static final RegistryObject<MobEffect> OFF_HAND_PERK = perk("off_hand", 3, 0x2f8f5b,
            PapRegistries.EXTRA_WEAPON_SLOTS, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e05", 1, AttributeModifier.Operation.ADDITION);
    public static final RegistryObject<MobEffect> JOCKEY_ENERGY = perk("jockey_energy", 4, 0xf2d64b,
            Attributes.MOVEMENT_SPEED, "8c3e1f52-6a0d-4b7e-9f21-3d5c7a9b1e06", 0.07, AttributeModifier.Operation.MULTIPLY_BASE);

    private PerkEffects() {}

    /** {@code icon} is the perk's cell on the icon sheet ({@code client/PerkEffectIcon}). */
    private static RegistryObject<MobEffect> perk(String id, int icon, int color, Attribute attribute, String uuid, double amount, AttributeModifier.Operation operation) {
        RegistryObject<MobEffect> effect = EFFECTS.register(id, () -> new PerkEffect(icon, color).addAttributeModifier(attribute, uuid, amount, operation));
        POTIONS.register(id, () -> new Potion(new MobEffectInstance(effect.get(), MobEffectInstance.INFINITE_DURATION)));
        return effect;
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
    }

    /** Heals the max health it adds and takes it back on removal, and re-evaluates the held gun when the fire rate changes. */
    private static final class PerkEffect extends MobEffect {
        private final int icon;

        PerkEffect(int icon, int color) {
            super(MobEffectCategory.BENEFICIAL, color);
            this.icon = icon;
        }

        @Override
        public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            float maxHealth = entity.getMaxHealth();
            super.addAttributeModifiers(entity, attributes, amplifier);
            if (entity.getMaxHealth() > maxHealth) {
                entity.heal(entity.getMaxHealth() - maxHealth);
            }
            refreshGun(entity);
        }

        @Override
        public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            super.removeAttributeModifiers(entity, attributes, amplifier);
            if (entity.getHealth() > entity.getMaxHealth()) {
                entity.setHealth(entity.getMaxHealth());
            }
            refreshGun(entity);
        }

        /** TacZ caches a gun's fire rate when it is drawn, so a change has to re-evaluate the gun in hand. */
        private void refreshGun(LivingEntity entity) {
            if (getAttributeModifiers().containsKey(PapRegistries.FIRE_RATE) && entity instanceof ServerPlayer player) {
                PerkGunEffects.refresh(player);
            }
        }

        @Override
        public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
            consumer.accept(new PerkEffectIcon(icon));
        }
    }
}
