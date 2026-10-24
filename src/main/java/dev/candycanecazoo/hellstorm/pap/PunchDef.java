package dev.candycanecazoo.hellstorm.pap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * What an item with this {@code punch_id} becomes at the upgrader. One entry of an upgrade file in
 * {@code data/<ns>/hellstorm/punch/} (see {@link PunchDefs}).
 * <pre>
 * "sword_mk2": { "item-id": "minecraft:diamond_sword", "nbt": { ... }, "name": "item.mymap.sword_upgraded", "cost": 2500,
 *   "carry-over": { "ammo": false, "attachments": true } }
 * </pre>
 * {@code item-id} is an item or a TacZ gun id. {@code nbt} is an object or an SNBT string. {@code name} is a lang key,
 * {@code cost} defaults to the machine's cost, and {@code carry-over} copies TacZ ammo and attachments from the old gun
 * when both are guns.
 */
public record PunchDef(ResourceLocation itemId, Optional<CompoundTag> nbt, Optional<String> name, Optional<Integer> cost, CarryOver carryOver) {
    public record CarryOver(boolean ammo, boolean attachments) {
        public static final CarryOver NONE = new CarryOver(false, false);

        public static final Codec<CarryOver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.BOOL, "ammo", false).forGetter(CarryOver::ammo),
                Codecs.strictOptional(Codec.BOOL, "attachments", false).forGetter(CarryOver::attachments)
        ).apply(i, CarryOver::new));
    }

    public static final Codec<PunchDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item-id").forGetter(PunchDef::itemId),
            Codecs.strictOptional(Codecs.COMPOUND, "nbt").forGetter(PunchDef::nbt),
            Codecs.strictOptional(Codec.STRING, "name").forGetter(PunchDef::name),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(PunchDef::cost),
            Codecs.strictOptional(CarryOver.CODEC, "carry-over", CarryOver.NONE).forGetter(PunchDef::carryOver)
    ).apply(i, PunchDef::new));

    /** The result against the installed TacZ guns. */
    public ItemSpec result() {
        return result(TaczBridge::gunExists);
    }

    /**
     * A TacZ gun id builds a full gun with ammo, punchable unless {@code nbt} sets {@code pap.punchable} false, so it can be
     * upgraded again. Anything else is the item with exactly {@code nbt}.
     */
    public ItemSpec result(Predicate<ResourceLocation> isGun) {
        if (!isGun.test(itemId)) {
            return new ItemSpec.Plain(itemId, 1, nbt);
        }
        boolean punchable = nbt.map(tag -> tag.getCompound(ItemSpec.PAP))
                .filter(pap -> pap.contains(ItemSpec.PUNCHABLE))
                .map(pap -> pap.getBoolean(ItemSpec.PUNCHABLE))
                .orElse(true);
        return new ItemSpec.Gun(itemId, false, punchable, nbt);
    }
}
