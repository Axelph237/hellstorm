package dev.candycanecazoo.packapunch.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Datapack binding from a game event to a Lua function: data/<ns>/packapunch/events/<id>.json. */
public record EventDef(String event, String script, String function, Filter filter, int priority) {
    public record Filter(Optional<Integer> roundMin, Optional<Integer> roundMax) {
        public static final Filter NONE = new Filter(Optional.empty(), Optional.empty());

        public static final Codec<Filter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("round_min").forGetter(Filter::roundMin),
                Codec.INT.optionalFieldOf("round_max").forGetter(Filter::roundMax)
        ).apply(i, Filter::new));

        public boolean accepts(int round) {
            return roundMin.map(min -> round >= min).orElse(true) && roundMax.map(max -> round <= max).orElse(true);
        }
    }

    public static final Codec<EventDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("event").forGetter(EventDef::event),
            Codec.STRING.fieldOf("script").forGetter(EventDef::script),
            Codec.STRING.fieldOf("function").forGetter(EventDef::function),
            Filter.CODEC.optionalFieldOf("filter", Filter.NONE).forGetter(EventDef::filter),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(EventDef::priority)
    ).apply(i, EventDef::new));
}
