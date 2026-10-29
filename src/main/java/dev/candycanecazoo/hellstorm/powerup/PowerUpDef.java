package dev.candycanecazoo.hellstorm.powerup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.resources.ResourceLocation;

/**
 * A power-up from {@code data/<ns>/hellstorm/powerups/<id>.json}. {@code name} is a lang key, {@code weight} its share of
 * drops (0 never drops at random, but can still be forced by a wave event), {@code durationTicks} how long a timed effect
 * lasts, and {@code item} the item the floating pickup shows, and its timer on the HUD, written as {@code /give} takes it.
 */
public record PowerUpDef(String name, int weight, int durationTicks, ItemSpec.Plain item, PowerUpEffect effect) {
    public static final ItemSpec.Plain DEFAULT_ITEM = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "nether_star"), 1, java.util.Optional.empty());

    public static final Codec<PowerUpDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PowerUpDef::name),
            Codecs.strictOptional(Codec.intRange(0, 1_000_000), "weight", 10).forGetter(PowerUpDef::weight),
            Codecs.strictOptional(Codec.intRange(1, 72_000), "duration_ticks", 600).forGetter(PowerUpDef::durationTicks),
            Codecs.strictOptional(ItemSpec.Plain.GIVE_CODEC, "item", DEFAULT_ITEM).forGetter(PowerUpDef::item),
            PowerUpEffect.CODEC.fieldOf("effect").forGetter(PowerUpDef::effect)
    ).apply(i, PowerUpDef::new));
}
