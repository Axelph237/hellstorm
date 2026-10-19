package dev.candycanecazoo.packapunch.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record FeatureRef(BlockPos pos, FeatureKind kind) {
    public static final Codec<FeatureRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(FeatureRef::pos),
            FeatureKind.CODEC.fieldOf("kind").forGetter(FeatureRef::kind)
    ).apply(i, FeatureRef::new));
}
