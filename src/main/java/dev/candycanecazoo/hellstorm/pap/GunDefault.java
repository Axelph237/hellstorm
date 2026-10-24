package dev.candycanecazoo.hellstorm.pap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;
import java.util.Locale;

/**
 * What the default upgrade also does to a TacZ gun of one class (pistol, rifle, ...), after its own changes. From an upgrade
 * file's {@code defaults}: {@code "rifle": { "nbt": { ... }, "method": "merge" }}.
 */
public record GunDefault(CompoundTag nbt, Method method) {
    public static final Codec<GunDefault> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.strictOptional(Codecs.COMPOUND, "nbt", new CompoundTag()).forGetter(GunDefault::nbt),
            Codecs.strictOptional(Method.CODEC, "method", Method.MODIFY).forGetter(GunDefault::method)
    ).apply(i, GunDefault::new));

    /** Changes {@code gunTag}, the gun's whole NBT. */
    public void applyTo(CompoundTag gunTag) {
        method.apply(gunTag, nbt);
    }

    /** How {@code nbt} changes the gun's NBT. Each goes into compounds on both sides, key by key. */
    public enum Method {
        /** Adds every given key, overwriting what's there. */
        MERGE {
            @Override
            void apply(CompoundTag target, CompoundTag given) {
                target.merge(given);
            }
        },
        /** Overwrites only keys the gun already has; adds nothing. */
        MODIFY {
            @Override
            void apply(CompoundTag target, CompoundTag given) {
                for (String key : given.getAllKeys()) {
                    Tag have = target.get(key);
                    Tag want = given.get(key);
                    if (have instanceof CompoundTag haveCompound && want instanceof CompoundTag wantCompound) {
                        apply(haveCompound, wantCompound);
                    } else if (have != null) {
                        target.put(key, want.copy());
                    }
                }
            }
        },
        /** Removes the given keys. A non-empty compound removes keys inside it; any other value, {@code {}} included, the key itself. */
        REMOVE {
            @Override
            void apply(CompoundTag target, CompoundTag given) {
                for (String key : given.getAllKeys()) {
                    if (given.get(key) instanceof CompoundTag inside && !inside.isEmpty()) {
                        if (target.get(key) instanceof CompoundTag have) {
                            apply(have, inside);
                        }
                    } else {
                        target.remove(key);
                    }
                }
            }
        };

        public static final Codec<Method> CODEC = Codec.STRING.comapFlatMap(Method::parse, Method::id);

        abstract void apply(CompoundTag target, CompoundTag given);

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static DataResult<Method> parse(String id) {
            return Arrays.stream(values()).filter(m -> m.id().equals(id)).findFirst().map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown method \"" + id + "\": use merge, modify or remove"));
        }
    }
}
