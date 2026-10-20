package dev.candycanecazoo.packapunch.pap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.util.Codecs;

import java.util.Optional;

/**
 * What an item with this {@code punch_id} becomes at the Pack-a-Punch machine. Loaded from
 * {@code data/<ns>/packapunch/punch/<id>.json}.
 * <pre>
 * { "result": { "item": "minecraft:diamond_sword" }, "name": "item.mymap.sword_upgraded", "cost": 2500,
 *   "carry_over": { "ammo": false, "attachments": true } }
 * </pre>
 * {@code cost} defaults to the machine's cost. {@code carry_over} copies TacZ ammo and attachments from the old gun when
 * both are guns.
 */
public record PunchDef(ItemSpec result, Optional<String> name, Optional<Integer> cost, CarryOver carryOver) {
    public record CarryOver(boolean ammo, boolean attachments) {
        public static final CarryOver NONE = new CarryOver(false, false);

        public static final Codec<CarryOver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.BOOL, "ammo", false).forGetter(CarryOver::ammo),
                Codecs.strictOptional(Codec.BOOL, "attachments", false).forGetter(CarryOver::attachments)
        ).apply(i, CarryOver::new));
    }

    public static final Codec<PunchDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemSpec.CODEC.fieldOf("result").forGetter(PunchDef::result),
            Codecs.strictOptional(Codec.STRING, "name").forGetter(PunchDef::name),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(PunchDef::cost),
            Codecs.strictOptional(CarryOver.CODEC, "carry_over", CarryOver.NONE).forGetter(PunchDef::carryOver)
    ).apply(i, PunchDef::new));
}
