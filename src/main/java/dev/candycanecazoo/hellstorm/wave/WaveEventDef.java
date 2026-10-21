package dev.candycanecazoo.hellstorm.wave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.util.Codecs;

import java.util.List;
import java.util.Optional;

/** A special round from {@code data/<ns>/hellstorm/wave_events/<id>.json}. {@code announce} is a lang key shown as it starts. */
public record WaveEventDef(WaveTrigger trigger, List<WaveEffect> effects, Optional<String> announce) {
    public static final Codec<WaveEventDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            WaveTrigger.CODEC.fieldOf("trigger").forGetter(WaveEventDef::trigger),
            WaveEffect.CODEC.listOf().fieldOf("effects").forGetter(WaveEventDef::effects),
            Codecs.strictOptional(Codec.STRING, "announce").forGetter(WaveEventDef::announce)
    ).apply(i, WaveEventDef::new));

    public WaveEventDef {
        effects = List.copyOf(effects);
    }
}
