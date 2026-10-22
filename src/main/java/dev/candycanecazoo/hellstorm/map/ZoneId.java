package dev.candycanecazoo.hellstorm.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Pattern;

public record ZoneId(String value) implements Comparable<ZoneId> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,32}");

    public static final Codec<ZoneId> CODEC = Codec.STRING.comapFlatMap(ZoneId::parse, ZoneId::value);

    public ZoneId {
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid room id '" + value + "': use 1-32 of a-z, 0-9, _");
        }
    }

    public static DataResult<ZoneId> parse(String value) {
        return VALID.matcher(value).matches()
                ? DataResult.success(new ZoneId(value))
                : DataResult.error(() -> "Invalid room id '" + value + "': use 1-32 of a-z, 0-9, _");
    }

    public static boolean isValid(String value) {
        return VALID.matcher(value).matches();
    }

    @Override
    public int compareTo(ZoneId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
