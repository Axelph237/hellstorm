package dev.candycanecazoo.packapunch.mob;

import dev.candycanecazoo.packapunch.util.Codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * One kind of zombie: which entity to spawn and how the game drives it. Datapack JSON at
 * {@code data/<ns>/packapunch/mob_profiles/<id>.json}. Any mob type works, including other mods' mobs.
 */
public record MobProfile(
        ResourceLocation entity,
        int weight,
        int minRound,
        Optional<Integer> maxRound,
        Ai ai,
        Optional<String> nbt,
        double healthMultiplier,
        double speedMultiplier,
        double damage,
        boolean breaksBarricades) {

    /** {@code replace} clears the mob's own goals; {@code augment} keeps them and adds the game's on top. */
    public enum Ai implements StringRepresentable {
        REPLACE, AUGMENT;

        public static final Codec<Ai> CODEC = StringRepresentable.fromEnum(Ai::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final Codec<MobProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(MobProfile::entity),
            Codecs.strictOptional(Codec.intRange(0, 1_000_000), "weight", 10).forGetter(MobProfile::weight),
            Codecs.strictOptional(Codec.intRange(1, 1_000_000), "min_round", 1).forGetter(MobProfile::minRound),
            Codecs.strictOptional(Codec.intRange(1, 1_000_000), "max_round").forGetter(MobProfile::maxRound),
            Codecs.strictOptional(Ai.CODEC, "ai", Ai.REPLACE).forGetter(MobProfile::ai),
            Codecs.strictOptional(Codec.STRING, "nbt").forGetter(MobProfile::nbt),
            Codecs.strictOptional(Codec.doubleRange(0.001, 1000), "health_multiplier", 1.0).forGetter(MobProfile::healthMultiplier),
            Codecs.strictOptional(Codec.doubleRange(0.001, 100), "speed_multiplier", 1.0).forGetter(MobProfile::speedMultiplier),
            Codecs.strictOptional(Codec.doubleRange(0, 10_000), "damage", 4.0).forGetter(MobProfile::damage),
            Codecs.strictOptional(Codec.BOOL, "breaks_barricades", true).forGetter(MobProfile::breaksBarricades)
    ).apply(i, MobProfile::new));

    /** Used when no datapack provides any profile, so a game always has something to spawn. */
    public static final ResourceLocation FALLBACK_ID = ResourceLocation.tryBuild("packapunch", "zombie");
    public static final MobProfile FALLBACK = new MobProfile(ResourceLocation.tryBuild("minecraft", "zombie"),
            10, 1, Optional.empty(), Ai.REPLACE, Optional.empty(), 1.0, 1.0, 4.0, true);

    public boolean allowedIn(int round) {
        return round >= minRound && maxRound.map(max -> round <= max).orElse(true);
    }
}
