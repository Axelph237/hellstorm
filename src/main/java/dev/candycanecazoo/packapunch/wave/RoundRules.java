package dev.candycanecazoo.packapunch.wave;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a wave event does to its round, folded from its effects: the only profiles that spawn (empty means the usual
 * roster), spawn rate and count multipliers, fog density, and a forced last-kill drop.
 */
public record RoundRules(ResourceLocation event, List<ResourceLocation> profiles, double spawnRate, double count, Optional<Float> fog,
                         Optional<ResourceLocation> lastKillDrop, Optional<String> announce) {
    public RoundRules {
        profiles = List.copyOf(profiles);
    }

    /** Effects of the same kind combine: multipliers multiply, profile lists join, and the last fog or drop wins. */
    public static RoundRules of(ResourceLocation id, WaveEventDef def) {
        List<ResourceLocation> profiles = new ArrayList<>();
        double spawnRate = 1;
        double count = 1;
        Optional<Float> fog = Optional.empty();
        Optional<ResourceLocation> drop = Optional.empty();
        for (WaveEffect effect : def.effects()) {
            if (effect instanceof WaveEffect.MobOverride override) {
                profiles.addAll(override.profiles());
            } else if (effect instanceof WaveEffect.SpawnRate rate) {
                spawnRate *= rate.multiplier();
            } else if (effect instanceof WaveEffect.Count c) {
                count *= c.multiplier();
            } else if (effect instanceof WaveEffect.Fog f) {
                fog = Optional.of(f.density());
            } else if (effect instanceof WaveEffect.LastKillDrop d) {
                drop = Optional.of(d.powerUp());
            }
        }
        return new RoundRules(id, profiles, spawnRate, count, fog, drop, def.announce());
    }
}
