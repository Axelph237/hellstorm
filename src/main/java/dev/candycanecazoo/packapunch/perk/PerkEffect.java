package dev.candycanecazoo.packapunch.perk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Locale;
import java.util.Map;

/** One thing a perk does, chosen by {@code type} in the perk's JSON. */
public sealed interface PerkEffect {
    String type();

    /** Adds to a player attribute while the perk is held: Juggernog's health, Stamin-Up's speed. */
    record Attribute(ResourceLocation attribute, double amount, AttributeModifier.Operation operation) implements PerkEffect {
        static final Codec<AttributeModifier.Operation> OPERATION = Codec.STRING.comapFlatMap(
                name -> switch (name) {
                    case "addition" -> DataResult.success(AttributeModifier.Operation.ADDITION);
                    case "multiply_base" -> DataResult.success(AttributeModifier.Operation.MULTIPLY_BASE);
                    case "multiply_total" -> DataResult.success(AttributeModifier.Operation.MULTIPLY_TOTAL);
                    default -> DataResult.error(() -> "Unknown operation " + name + " (addition, multiply_base, multiply_total)");
                },
                operation -> operation.name().toLowerCase(Locale.ROOT));

        static final Codec<Attribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("attribute").forGetter(Attribute::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Attribute::amount),
                Codecs.strictOptional(OPERATION, "operation", AttributeModifier.Operation.ADDITION).forGetter(Attribute::operation)
        ).apply(i, Attribute::new));

        public String type() {
            return "attribute_modifier";
        }
    }

    /** Multiplies the damage of the player's TacZ gun hits. */
    record DamageMultiplier(double multiplier) implements PerkEffect {
        public String type() {
            return "damage_multiplier";
        }
    }

    /** Multiplies the fire rate of the player's TacZ guns (Double Tap). */
    record RpmMultiplier(double multiplier) implements PerkEffect {
        public String type() {
            return "rpm_multiplier";
        }
    }

    /** Extra TacZ gun slots (Mule Kick). */
    record WeaponSlots(int slots) implements PerkEffect {
        static final Codec<WeaponSlots> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 9).fieldOf("slots").forGetter(WeaponSlots::slots)
        ).apply(i, WeaponSlots::new));

        public String type() {
            return "weapon_slots";
        }
    }

    /** Multiplies how long the player takes to revive others (Quick Revive). Stored for the downs feature; no effect yet. */
    record ReviveSpeed(double multiplier) implements PerkEffect {
        public String type() {
            return "revive_speed";
        }
    }

    /** Lets a solo player revive themselves (Quick Revive). Stored for the downs feature; no effect yet. */
    record SelfRevive() implements PerkEffect {
        public String type() {
            return "self_revive";
        }
    }

    private static Codec<Double> multiplier() {
        return Codec.doubleRange(0.01, 100);
    }

    Map<String, Codec<? extends PerkEffect>> TYPES = Map.of(
            "attribute_modifier", Attribute.CODEC,
            "damage_multiplier", RecordCodecBuilder.<DamageMultiplier>create(i -> i.group(
                    multiplier().fieldOf("multiplier").forGetter(DamageMultiplier::multiplier)).apply(i, DamageMultiplier::new)),
            "rpm_multiplier", RecordCodecBuilder.<RpmMultiplier>create(i -> i.group(
                    multiplier().fieldOf("multiplier").forGetter(RpmMultiplier::multiplier)).apply(i, RpmMultiplier::new)),
            "weapon_slots", WeaponSlots.CODEC,
            "revive_speed", RecordCodecBuilder.<ReviveSpeed>create(i -> i.group(
                    multiplier().fieldOf("multiplier").forGetter(ReviveSpeed::multiplier)).apply(i, ReviveSpeed::new)),
            "self_revive", Codec.unit(new SelfRevive()));

    Codec<PerkEffect> CODEC = Codec.STRING.partialDispatch("type",
            effect -> DataResult.success(effect.type()),
            type -> TYPES.containsKey(type) ? DataResult.success(TYPES.get(type))
                    : DataResult.error(() -> "Unknown perk effect type " + type + " (" + String.join(", ", new java.util.TreeSet<>(TYPES.keySet())) + ")"));
}
