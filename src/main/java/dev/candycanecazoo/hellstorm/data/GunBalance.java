package dev.candycanecazoo.hellstorm.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.util.Codecs;

/**
 * How Hellstorm changes one TacZ gun: its damage, and its reserve ammo, both as multipliers of TacZ's own. The reserve is
 * {@code players.reserve_magazines} magazines times {@code reserve_multiplier}, so the setting still scales every gun.
 */
public record GunBalance(double damageMultiplier, double reserveMultiplier) {
    public static final GunBalance NONE = new GunBalance(1.0, 1.0);

    public static final Codec<GunBalance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.strictOptional(Codec.doubleRange(0.01, 100), "damage_multiplier", 1.0).forGetter(GunBalance::damageMultiplier),
            Codecs.strictOptional(Codec.doubleRange(0, 100), "reserve_multiplier", 1.0).forGetter(GunBalance::reserveMultiplier)
    ).apply(i, GunBalance::new));

    /** The reserve, in rounds, for a gun holding {@code magazine} rounds. */
    public int reserve(int magazine, int reserveMagazines) {
        return (int) Math.round(magazine * reserveMagazines * reserveMultiplier);
    }
}
