package dev.candycanecazoo.hellstorm.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Optional fields that fail loudly. In 1.20.1, {@code Codec#optionalFieldOf} quietly uses the default when a field is
 * present but invalid, so a typo like {@code "ai": "agument"} would silently become the default. These only use the
 * default when the field is absent. Use them instead of {@code optionalFieldOf} (enforced by CodecLintTest).
 */
public final class Codecs {
    private Codecs() {}

    /**
     * NBT written as a JSON object or as an SNBT string, {@code "{Damage:3,display:{Lore:['\"Hot\"']}}"}. In an object,
     * objects become compounds, whole numbers the smallest integer tag that holds them, other numbers floats or doubles,
     * and booleans bytes. Encodes as an object.
     */
    public static final Codec<CompoundTag> COMPOUND = Codec.PASSTHROUGH.flatXmap(
            dynamic -> dynamic.asString().result().map(Codecs::parseSnbt).orElseGet(() -> CompoundTag.CODEC.parse(dynamic)),
            tag -> DataResult.<Dynamic<?>>success(new Dynamic<>(NbtOps.INSTANCE, tag)));

    private static DataResult<CompoundTag> parseSnbt(String text) {
        try {
            return DataResult.success(TagParser.parseTag(text));
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> "Bad NBT " + text + ": " + e.getMessage());
        }
    }

    public static <A> MapCodec<Optional<A>> strictOptional(Codec<A> codec, String name) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    return DataResult.success(Optional.empty());
                }
                return codec.parse(ops, value).map(Optional::of).mapError(error -> name + ": " + error);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get()).mapError(error -> name + ": " + error)) : prefix;
            }

            @Override
            public String toString() {
                return "StrictOptional[" + name + "]";
            }
        };
    }

    public static <A> MapCodec<A> strictOptional(Codec<A> codec, String name, A defaultValue) {
        return strictOptional(codec, name).xmap(
                value -> value.orElse(defaultValue),
                value -> Objects.equals(value, defaultValue) ? Optional.empty() : Optional.of(value));
    }
}
