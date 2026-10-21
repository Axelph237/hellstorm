package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.resources.ResourceLocation;

/** The team-wide timed effects the game reads (see {@link Game#startTimed}). Keyed by effect, not by power-up. */
public final class TimedEffects {
    public static final ResourceLocation INSTA_KILL = Hellstorm.id("insta_kill");
    public static final ResourceLocation DOUBLE_POINTS = Hellstorm.id("double_points");
    public static final ResourceLocation BLACK_FRIDAY = Hellstorm.id("black_friday");

    private TimedEffects() {}
}
