package dev.candycanecazoo.packapunch.perk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * One perk a machine can sell. Datapack JSON at {@code data/<ns>/packapunch/perks/<id>.json}. {@code name} is a lang key,
 * so a resource pack can rename the default perks.
 */
public record PerkDef(String name, int cost, boolean requiresPower, int color, List<PerkEffect> effects) {
    /** {@code "#rrggbb"}. */
    public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(PerkDef::parseColor, rgb -> String.format("#%06x", rgb));

    public static final Codec<PerkDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PerkDef::name),
            Codec.intRange(0, 1_000_000).fieldOf("cost").forGetter(PerkDef::cost),
            Codecs.strictOptional(Codec.BOOL, "requires_power", true).forGetter(PerkDef::requiresPower),
            Codecs.strictOptional(COLOR, "color", 0xFFFFFF).forGetter(PerkDef::color),
            PerkEffect.CODEC.listOf().fieldOf("effects").forGetter(PerkDef::effects)
    ).apply(i, PerkDef::new));

    public PerkDef {
        effects = List.copyOf(effects);
    }

    /** The same id for the same perk and effect every time, so the modifier a perk added is exactly the one removed. */
    public static UUID modifierId(ResourceLocation perk, int effectIndex) {
        return UUID.nameUUIDFromBytes(("packapunch:perk/" + perk + "/" + effectIndex).getBytes(StandardCharsets.UTF_8));
    }

    private static DataResult<Integer> parseColor(String text) {
        return text.matches("#[0-9a-fA-F]{6}") ? DataResult.success(Integer.parseInt(text.substring(1), 16))
                : DataResult.error(() -> "Color must be #rrggbb, not " + text);
    }
}
