package dev.candycanecazoo.hellstorm.powerup;

import com.mojang.serialization.Codec;
import dev.candycanecazoo.hellstorm.game.TimedEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a power-up does when picked up. Timed ones run for the power-up's {@code duration_ticks} under their
 * {@link TimedEffects} id, so two power-ups with the same effect share one timer; the rest happen at once.
 */
public enum PowerUpEffect implements StringRepresentable {
    MAX_AMMO(null), INSTA_KILL(TimedEffects.INSTA_KILL), DOUBLE_POINTS(TimedEffects.DOUBLE_POINTS), FAT_MAN(null), PAX_ROMANA(null),
    BLACK_FRIDAY(TimedEffects.BLACK_FRIDAY);

    private static final Codec<PowerUpEffect> TYPE = StringRepresentable.fromEnum(PowerUpEffect::values);

    /** {@code {"type": "insta_kill"}}: an object, so effects can take parameters later without changing the format. */
    public static final Codec<PowerUpEffect> CODEC = TYPE.fieldOf("type").codec();

    private final ResourceLocation timer;

    PowerUpEffect(ResourceLocation timer) {
        this.timer = timer;
    }

    /** The timed effect this starts, if it's timed. */
    public Optional<ResourceLocation> timer() {
        return Optional.ofNullable(timer);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
