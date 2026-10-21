package dev.candycanecazoo.hellstorm.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * An item the mod hands out: from wall-buys, the Mystery Crate, Pack-a-Punch results, and loadouts. Either any item with a
 * count and optional NBT, or the {@code tacz_gun} shorthand, which builds a full TacZ gun.
 * <pre>
 * { "item": "minecraft:golden_apple", "count": 2, "nbt": { ... } }
 * { "tacz_gun": "tacz:ak47", "unlimited_ammo": false, "punchable": true }
 * </pre>
 */
public sealed interface ItemSpec {
    /** The {@code pap} compound on an item: whether Pack-a-Punch accepts it, and which upgrade it gets. */
    String PAP = "pap";
    String PUNCHABLE = "punchable";
    String PUNCH_ID = "punch_id";

    record Plain(ResourceLocation item, int count, Optional<CompoundTag> nbt) implements ItemSpec {
        static final Codec<Plain> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Plain::item),
                Codecs.strictOptional(Codec.intRange(1, 64), "count", 1).forGetter(Plain::count),
                Codecs.strictOptional(CompoundTag.CODEC, "nbt").forGetter(Plain::nbt)
        ).apply(i, Plain::new));

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

    /** A TacZ gun. Guns are punchable unless the spec says otherwise. */
    record Gun(ResourceLocation gunId, boolean unlimitedAmmo, boolean punchable) implements ItemSpec {
        static final Codec<Gun> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("tacz_gun").forGetter(Gun::gunId),
                Codecs.strictOptional(Codec.BOOL, "unlimited_ammo", false).forGetter(Gun::unlimitedAmmo),
                Codecs.strictOptional(Codec.BOOL, PUNCHABLE, true).forGetter(Gun::punchable)
        ).apply(i, Gun::new));

        public static Gun of(ResourceLocation gunId) {
            return new Gun(gunId, false, true);
        }

        @Override
        public Optional<ItemStack> build(int reserveMagazines) {
            return TaczBridge.createGun(gunId, reserveMagazines).map(stack -> {
                if (unlimitedAmmo) {
                    TaczBridge.setUnlimitedAmmo(stack, true);
                }
                if (punchable) {
                    stack.getOrCreateTagElement(PAP).putBoolean(PUNCHABLE, true);
                }
                return stack;
            });
        }

        /** Any gun with this id, upgraded or not, so a refill works on a Pack-a-Punched copy too. */
        @Override
        public boolean matches(ItemStack stack) {
            return TaczBridge.inspect(stack).map(info -> info.gunId().equals(gunId)).orElse(false);
        }

        @Override
        public String describe() {
            return gunId.toString();
        }
    }

    Codec<ItemSpec> CODEC = Codec.either(Gun.CODEC, Plain.CODEC).xmap(
            either -> either.map(gun -> (ItemSpec) gun, plain -> plain),
            spec -> spec instanceof Gun gun ? Either.left(gun) : Either.right((Plain) spec));

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
