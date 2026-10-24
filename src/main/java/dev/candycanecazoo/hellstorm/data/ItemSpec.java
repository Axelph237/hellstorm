package dev.candycanecazoo.hellstorm.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * An item the mod hands out: from wall-buys, the Mystery Crate, upgrader results, and loadouts. Either any item with a
 * count and optional NBT, or the {@code tacz_gun} shorthand, which builds a full TacZ gun with optional NBT merged onto it.
 * An item can also be written as {@code /give} takes it, one of them.
 * <pre>
 * { "item": "minecraft:golden_apple", "count": 2, "nbt": { ... } }
 * "lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}"
 * { "tacz_gun": "tacz:ak47", "unlimited_ammo": false, "punchable": true, "nbt": { ... } }
 * </pre>
 */
public sealed interface ItemSpec {
    /** The {@code pap} compound on an item: whether the upgrader accepts it, and which upgrade it gets. */
    String PAP = "pap";
    String PUNCHABLE = "punchable";
    String PUNCH_ID = "punch_id";

    record Plain(ResourceLocation item, int count, Optional<CompoundTag> nbt) implements ItemSpec {
        static final Codec<Plain> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Plain::item),
                Codecs.strictOptional(Codec.intRange(1, 64), "count", 1).forGetter(Plain::count),
                Codecs.strictOptional(CompoundTag.CODEC, "nbt").forGetter(Plain::nbt)
        ).apply(i, Plain::new));

        /** One item written the way {@code /give} takes it, {@code id} or {@code id{nbt}}, as config lists do. */
        public static final Codec<Plain> GIVE_CODEC = Codec.STRING.comapFlatMap(Plain::parse, Plain::asGive);

        public static DataResult<Plain> parse(String text) {
            int brace = text.indexOf('{');
            ResourceLocation item = ResourceLocation.tryParse(brace < 0 ? text : text.substring(0, brace));
            if (item == null) {
                return DataResult.error(() -> "Not an item id: " + text);
            }
            if (brace < 0) {
                return DataResult.success(new Plain(item, 1, Optional.empty()));
            }
            try {
                return DataResult.success(new Plain(item, 1, Optional.of(TagParser.parseTag(text.substring(brace)))));
            } catch (CommandSyntaxException e) {
                return DataResult.error(() -> "Bad NBT in " + text + ": " + e.getMessage());
            }
        }

        /** The {@code /give} form. Drops the count. */
        public String asGive() {
            return item + nbt.map(CompoundTag::toString).orElse("");
        }

        @Override
        public Optional<ItemStack> build(int reserveMagazines) {
            if (!ForgeRegistries.ITEMS.containsKey(item)) {
                return Optional.empty();
            }
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(item), count);
            nbt.ifPresent(tag -> stack.setTag(tag.copy()));
            return Optional.of(stack);
        }

        /** Same item, and the stack carries at least the spec's NBT. */
        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && item.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    && nbt.map(tag -> NbtUtils.compareNbt(tag, stack.getTag(), true)).orElse(true);
        }

        @Override
        public String describe() {
            return (count > 1 ? count + "x " : "") + item;
        }
    }

    /** A TacZ gun, with {@code nbt} merged onto it once built. Guns are punchable unless the spec says otherwise. */
    record Gun(ResourceLocation gunId, boolean unlimitedAmmo, boolean punchable, Optional<CompoundTag> nbt) implements ItemSpec {
        static final Codec<Gun> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("tacz_gun").forGetter(Gun::gunId),
                Codecs.strictOptional(Codec.BOOL, "unlimited_ammo", false).forGetter(Gun::unlimitedAmmo),
                Codecs.strictOptional(Codec.BOOL, PUNCHABLE, true).forGetter(Gun::punchable),
                Codecs.strictOptional(CompoundTag.CODEC, "nbt").forGetter(Gun::nbt)
        ).apply(i, Gun::new));

        /** A gun id written the way {@code /give} writes an item, {@code tacz:glock_17} or {@code tacz:glock_17{nbt}}, as config lists do. */
        public static final Codec<Gun> GIVE_CODEC = Codec.STRING.comapFlatMap(Gun::parse, Gun::asGive);

        public Gun(ResourceLocation gunId, boolean unlimitedAmmo, boolean punchable) {
            this(gunId, unlimitedAmmo, punchable, Optional.empty());
        }

        public static DataResult<Gun> parse(String text) {
            return Plain.parse(text).map(plain -> new Gun(plain.item(), false, true, plain.nbt()));
        }

        public String asGive() {
            return gunId + nbt.map(CompoundTag::toString).orElse("");
        }

        public static Gun of(ResourceLocation gunId) {
            return new Gun(gunId, false, true);
        }

        @Override
        public Optional<ItemStack> build(int reserveMagazines) {
            return TaczBridge.createGun(gunId, reserveMagazines).map(stack -> {
                nbt.ifPresent(tag -> stack.getOrCreateTag().merge(tag.copy()));
                if (unlimitedAmmo) {
                    TaczBridge.setUnlimitedAmmo(stack, true);
                }
                if (punchable) {
                    stack.getOrCreateTagElement(PAP).putBoolean(PUNCHABLE, true);
                }
                return stack;
            });
        }

        /** Any gun with this id, upgraded or not, so a refill works on an upgraded copy too. */
        @Override
        public boolean matches(ItemStack stack) {
            return TaczBridge.inspect(stack).map(info -> info.gunId().equals(gunId)).orElse(false);
        }

        @Override
        public String describe() {
            return gunId.toString();
        }
    }

    /** Written out as an object, read from an object or the {@code /give} form. */
    Codec<ItemSpec> CODEC = Codec.either(Codec.either(Gun.CODEC, Plain.CODEC), Plain.GIVE_CODEC).xmap(
            either -> either.map(object -> object.map(gun -> (ItemSpec) gun, plain -> plain), plain -> plain),
            spec -> Either.left(spec instanceof Gun gun ? Either.left(gun) : Either.right((Plain) spec)));

    /** A spec that sells copies of {@code stack}: its TacZ gun, or the item with its count and NBT. */
    static ItemSpec of(ItemStack stack) {
        return TaczBridge.inspect(stack).<ItemSpec>map(info -> new Gun(info.gunId(), TaczBridge.hasUnlimitedAmmo(stack), true))
                .orElseGet(() -> new Plain(ForgeRegistries.ITEMS.getKey(stack.getItem()), stack.getCount(), Optional.ofNullable(stack.getTag()).map(CompoundTag::copy)));
    }

    /** Empty when the item or gun isn't installed. */
    Optional<ItemStack> build(int reserveMagazines);

    boolean matches(ItemStack stack);

    /** For prompts and logs until items have display names here. */
    String describe();
}
