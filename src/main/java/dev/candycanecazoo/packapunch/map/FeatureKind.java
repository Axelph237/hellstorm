package dev.candycanecazoo.packapunch.map;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Kinds of feature block whose position the map indexes. Their config lives in their BlockEntity. */
public enum FeatureKind implements StringRepresentable {
    SPAWN("spawn");

    public static final Codec<FeatureKind> CODEC = StringRepresentable.fromEnum(FeatureKind::values);

    private final String name;

    FeatureKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
