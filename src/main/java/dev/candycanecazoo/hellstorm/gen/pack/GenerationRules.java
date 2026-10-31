package dev.candycanecazoo.hellstorm.gen.pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.gen.layout.Rules;
import dev.candycanecazoo.hellstorm.util.Codecs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A pack's {@code generation.json}: how big a map is, which tag starts it, what each tag allows, how dense each
 * placeholder's machine is and what it's set to, what joined doors cost, and what walls up an opening nothing joined.
 */
public record GenerationRules(Range rooms, String start, List<TagSpec> tags, Map<PlaceholderKind, FeatureRule> features, DoorRule doors, String seal) {
    /** A whole-number range, both ends in. */
    public record Range(int min, int max) {
        public static final Codec<Range> CODEC = RecordCodecBuilder.<Range>create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(Range::min),
                Codec.INT.fieldOf("max").forGetter(Range::max)
        ).apply(i, Range::new)).comapFlatMap(r -> r.min() <= r.max() && r.min() >= 0
                ? DataResult.success(r) : DataResult.error(() -> "A range's min must be 0 or more and no more than its max: " + r), r -> r);

        public boolean contains(int n) {
            return n >= min && n <= max;
        }
    }

    /** Depths (rooms from the start) something may be at: from {@code min}, up to {@code max} if given. */
    public record Depths(int min, Optional<Integer> max) {
        public static final Depths ANY = new Depths(0, Optional.empty());
        public static final Codec<Depths> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.INT, "min", 0).forGetter(Depths::min),
                Codecs.strictOptional(Codec.INT, "max").forGetter(Depths::max)
        ).apply(i, Depths::new));

        public boolean contains(int depth) {
            return depth >= min && max.map(m -> depth <= m).orElse(true);
        }
    }

    /** A tag's rule: at least {@code min} rooms with it and at most {@code max}, each at one of {@code depth}. */
    public record TagSpec(String tag, int min, Optional<Integer> max, Depths depth) {
        public static final Codec<TagSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(TagSpec::tag),
                Codecs.strictOptional(Codec.INT, "min", 0).forGetter(TagSpec::min),
                Codecs.strictOptional(Codec.INT, "max").forGetter(TagSpec::max),
                Codecs.strictOptional(Depths.CODEC, "depth", Depths.ANY).forGetter(TagSpec::depth)
        ).apply(i, TagSpec::new));
    }

    /**
     * One thing a placeholder can become: {@code item} as {@code /give} takes it (the gun a wall-buy sells, the drink a
     * vending machine sells), how often it's picked, at which depths, and its price if not the default.
     */
    public record PoolEntry(String item, int weight, Depths depth, Optional<Integer> cost) {
        public static final Codec<PoolEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(PoolEntry::item),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "weight", 1).forGetter(PoolEntry::weight),
                Codecs.strictOptional(Depths.CODEC, "depth", Depths.ANY).forGetter(PoolEntry::depth),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(PoolEntry::cost)
        ).apply(i, PoolEntry::new));
    }

    /**
     * How many of a kind's placeholders become its machine ({@code count}, drawn from its range; without one, every
     * placeholder at the allowed depths), at which depths, and what they're set to ({@code pool}; {@code unique} picks
     * each entry at most once, for one of each perk).
     */
    public record FeatureRule(Optional<Range> count, Depths depth, List<PoolEntry> pool, boolean unique) {
        public static final FeatureRule EVERY = new FeatureRule(Optional.empty(), Depths.ANY, List.of(), false);
        public static final Codec<FeatureRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Range.CODEC, "count").forGetter(FeatureRule::count),
                Codecs.strictOptional(Depths.CODEC, "depth", Depths.ANY).forGetter(FeatureRule::depth),
                Codecs.strictOptional(PoolEntry.CODEC.listOf(), "pool", List.of()).forGetter(FeatureRule::pool),
                Codecs.strictOptional(Codec.BOOL, "unique", false).forGetter(FeatureRule::unique)
        ).apply(i, FeatureRule::new));
    }

    /**
     * What a door between two rooms costs: {@code baseCost} plus {@code costPerDepth} for each room the farther side is
     * from the start; the block it's built of; and from which depth it needs the power, if any.
     */
    public record DoorRule(int baseCost, int costPerDepth, String block, Optional<Integer> powerFromDepth) {
        public static final DoorRule DEFAULT = new DoorRule(750, 250, "minecraft:oak_planks", Optional.empty());
        public static final Codec<DoorRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "base_cost", DEFAULT.baseCost()).forGetter(DoorRule::baseCost),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost_per_depth", DEFAULT.costPerDepth()).forGetter(DoorRule::costPerDepth),
                Codecs.strictOptional(Codec.STRING, "block", DEFAULT.block()).forGetter(DoorRule::block),
                Codecs.strictOptional(Codec.INT, "power_from_depth").forGetter(DoorRule::powerFromDepth)
        ).apply(i, DoorRule::new));

        public int cost(int depth) {
            return baseCost + costPerDepth * Math.max(0, depth - 1);
        }
    }

    public static final String DEFAULT_SEAL = "minecraft:bricks";
    public static final GenerationRules DEFAULT = new GenerationRules(new Range(8, 14), "start", List.of(), Map.of(), DoorRule.DEFAULT, DEFAULT_SEAL);

    public static final Codec<GenerationRules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.strictOptional(Range.CODEC, "rooms", DEFAULT.rooms()).forGetter(GenerationRules::rooms),
            Codecs.strictOptional(Codec.STRING, "start", DEFAULT.start()).forGetter(GenerationRules::start),
            Codecs.strictOptional(TagSpec.CODEC.listOf(), "tags", List.of()).forGetter(GenerationRules::tags),
            Codecs.strictOptional(Codec.unboundedMap(PlaceholderKind.CODEC, FeatureRule.CODEC), "features", Map.of()).forGetter(GenerationRules::features),
            Codecs.strictOptional(DoorRule.CODEC, "doors", DoorRule.DEFAULT).forGetter(GenerationRules::doors),
            Codecs.strictOptional(Codec.STRING, "seal", DEFAULT_SEAL).forGetter(GenerationRules::seal)
    ).apply(i, GenerationRules::new));

    public GenerationRules {
        tags = List.copyOf(tags);
        features = Map.copyOf(features);
    }

    /** The rule for {@code kind}: every placeholder of it becomes its machine unless the pack says otherwise. */
    public FeatureRule feature(PlaceholderKind kind) {
        return features.getOrDefault(kind, FeatureRule.EVERY);
    }

    /** What the layout must meet. */
    public Rules layoutRules() {
        if (rooms.min() < 1) {
            return new Rules(1, Math.max(1, rooms.max()), start, layoutTags());
        }
        return new Rules(rooms.min(), rooms.max(), start, layoutTags());
    }

    private List<Rules.TagRule> layoutTags() {
        return tags.stream().map(t -> new Rules.TagRule(t.tag(), t.min(), t.max(), t.depth().min(), t.depth().max())).toList();
    }
}
