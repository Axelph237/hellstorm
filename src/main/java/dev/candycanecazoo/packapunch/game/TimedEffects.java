package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.resources.ResourceLocation;

/** The team-wide timed effects the game reads (see {@link Game#startTimed}). Keyed by effect, not by power-up. */
public final class TimedEffects {
    public static final ResourceLocation INSTA_KILL = PackAPunch.id("insta_kill");
    public static final ResourceLocation DOUBLE_POINTS = PackAPunch.id("double_points");
    public static final ResourceLocation FIRE_SALE = PackAPunch.id("fire_sale");

    private TimedEffects() {}
}
