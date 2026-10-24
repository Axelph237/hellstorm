package dev.candycanecazoo.hellstorm.mob;

import dev.candycanecazoo.hellstorm.util.Codecs;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * One kind of zombie: which entity to spawn and how the game drives it. Datapack JSON at
 * {@code data/<ns>/hellstorm/mob_profiles/<id>.json}. Any mob type works, including other mods' mobs. {@code scale} sizes
 * the mob, its hitbox and how it's drawn ({@link MobScale}).
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
        boolean breaksBarricades,
        Navigation navigation,
        double scale) {

    /** {@code replace} clears the mob's own goals; {@code augment} keeps them and adds the game's on top. */
    public enum Ai implements StringRepresentable {
        REPLACE, AUGMENT;

        public static final Codec<Ai> CODEC = StringRepresentable.fromEnum(Ai::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /**
     * {@code flow} walks on vanilla navigation with real collision checks, guided down the game's distance field toward a
     * far player. {@code vanilla} uses the mob's own navigation, straight at the player, with a long follow range. Mobs that
     * don't walk (fliers, swimmers) always use their own navigation.
     */
    public enum Navigation implements StringRepresentable {
        FLOW, VANILLA;

        public static final Codec<Navigation> CODEC = StringRepresentable.fromEnum(Navigation::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /** SNBT the mob spawns with. It must parse, and a crawler tag's speed, if given, must be a number above 0. */
    private static final Codec<String> NBT = Codec.STRING.flatXmap(MobProfile::checkNbt, MobProfile::checkNbt);

    public static final Codec<MobProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(MobProfile::entity),
            Codecs.strictOptional(Codec.intRange(0, 1_000_000), "weight", 10).forGetter(MobProfile::weight),
            Codecs.strictOptional(Codec.intRange(1, 1_000_000), "min_round", 1).forGetter(MobProfile::minRound),
            Codecs.strictOptional(Codec.intRange(1, 1_000_000), "max_round").forGetter(MobProfile::maxRound),
            Codecs.strictOptional(Ai.CODEC, "ai", Ai.REPLACE).forGetter(MobProfile::ai),
            Codecs.strictOptional(NBT, "nbt").forGetter(MobProfile::nbt),
            Codecs.strictOptional(Codec.doubleRange(0.001, 1000), "health_multiplier", 1.0).forGetter(MobProfile::healthMultiplier),
            Codecs.strictOptional(Codec.doubleRange(0.001, 100), "speed_multiplier", 1.0).forGetter(MobProfile::speedMultiplier),
            Codecs.strictOptional(Codec.doubleRange(0, 10_000), "damage", 4.0).forGetter(MobProfile::damage),
            Codecs.strictOptional(Codec.BOOL, "breaks_barricades", true).forGetter(MobProfile::breaksBarricades),
            Codecs.strictOptional(Navigation.CODEC, "navigation", Navigation.FLOW).forGetter(MobProfile::navigation),
            Codecs.strictOptional(Codec.doubleRange(0.1, 10), "scale", 1.0).forGetter(MobProfile::scale)
    ).apply(i, MobProfile::new));

    /** Used when no datapack provides any profile, so a game always has something to spawn. */
    public static final ResourceLocation FALLBACK_ID = ResourceLocation.tryBuild("hellstorm", "zombie");
    public static final MobProfile FALLBACK = new MobProfile(ResourceLocation.tryBuild("minecraft", "zombie"),
            10, 1, Optional.empty(), Ai.REPLACE, Optional.empty(), 1.0, 1.0, 4.0, true, Navigation.FLOW, 1.0);

    private static DataResult<String> checkNbt(String text) {
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(text);
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> "Bad NBT " + text + ": " + e.getMessage());
        }
        CompoundTag crawler = tag.getCompound("ForgeData").getCompound(Limbs.CRAWLER);
        if (crawler.contains(Limbs.SPEED) && !(crawler.contains(Limbs.SPEED, Tag.TAG_ANY_NUMERIC) && crawler.getFloat(Limbs.SPEED) > 0)) {
            return DataResult.error(() -> Limbs.CRAWLER + "." + Limbs.SPEED + " must be a number above 0, not " + crawler.get(Limbs.SPEED));
        }
        return DataResult.success(text);
    }

    /** The hitbox this profile's mob of {@code type} spawns with, at its {@link #scale}. */
    public EntityDimensions hitbox(EntityType<?> type) {
        return type.getDimensions().scale((float) scale);
    }

    public boolean allowedIn(int round) {
        return round >= minRound && maxRound.map(max -> round <= max).orElse(true);
    }
}
