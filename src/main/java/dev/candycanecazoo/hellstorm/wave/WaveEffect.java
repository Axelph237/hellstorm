package dev.candycanecazoo.hellstorm.wave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** What a wave event changes about its round. */
public sealed interface WaveEffect {
    String type();

    /** Only these mob profiles spawn this round (any weight, so a profile with weight 0 can be a special-round-only mob). */
    record MobOverride(List<ResourceLocation> profiles) implements WaveEffect {
        public MobOverride {
            profiles = List.copyOf(profiles);
        }

        public String type() {
            return "mob_override";
        }
    }

    /** Spawns come this many times as often. */
    record SpawnRate(double multiplier) implements WaveEffect {
        public String type() {
            return "spawn_rate";
        }
    }

    /** The round has this many times its usual zombies. */
    record Count(double multiplier) implements WaveEffect {
        public String type() {
            return "count";
        }
    }

    /** Fog closes in on every player, from 0 (none) to 1 (a few blocks). */
    record Fog(float density) implements WaveEffect {
        public String type() {
            return "fog";
        }
    }

    /** The round's last kill drops this power-up, on top of any random drops. */
    record LastKillDrop(ResourceLocation powerUp) implements WaveEffect {
        public String type() {
            return "last_kill_drop";
        }
    }

    private static Codec<Double> multiplier() {
        return Codec.doubleRange(0.01, 100);
    }

    Map<String, Codec<? extends WaveEffect>> TYPES = Map.of(
            "mob_override", RecordCodecBuilder.<MobOverride>create(i -> i.group(
                    ResourceLocation.CODEC.listOf().fieldOf("profiles").forGetter(MobOverride::profiles)).apply(i, MobOverride::new)),
            "spawn_rate", RecordCodecBuilder.<SpawnRate>create(i -> i.group(
                    multiplier().fieldOf("multiplier").forGetter(SpawnRate::multiplier)).apply(i, SpawnRate::new)),
            "count", RecordCodecBuilder.<Count>create(i -> i.group(
                    multiplier().fieldOf("multiplier").forGetter(Count::multiplier)).apply(i, Count::new)),
            "fog", RecordCodecBuilder.<Fog>create(i -> i.group(
                    Codec.floatRange(0, 1).fieldOf("density").forGetter(Fog::density)).apply(i, Fog::new)),
            "last_kill_drop", RecordCodecBuilder.<LastKillDrop>create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("powerup").forGetter(LastKillDrop::powerUp)).apply(i, LastKillDrop::new)));

    Codec<WaveEffect> CODEC = Codec.STRING.partialDispatch("type",
            effect -> DataResult.success(effect.type()),
            type -> TYPES.containsKey(type) ? DataResult.success(TYPES.get(type))
                    : DataResult.error(() -> "Unknown wave effect type " + type + " (" + String.join(", ", new java.util.TreeSet<>(TYPES.keySet())) + ")"));
}
