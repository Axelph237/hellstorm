package dev.candycanecazoo.hellstorm.gen.pack;

import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.Locale;

/**
 * What a placeholder block in a room stands for. At generation each becomes its machine, configured from the pack's
 * {@code generation.json}, or is taken out if the pack's density rules don't pick it.
 */
public enum PlaceholderKind {
    WALL_BUY, VENDING_MACHINE, MYSTERY_BOX, UPGRADER, POWER_SWITCH;

    public static final Codec<PlaceholderKind> CODEC = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(values()).filter(k -> k.id().equals(name)).findFirst()
                    .map(com.mojang.serialization.DataResult::success)
                    .orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Not a placeholder: " + name)),
            PlaceholderKind::id);

    /** Its name in files and block ids: {@code wall_buy}, {@code vending_machine}, ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
