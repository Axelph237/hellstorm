package dev.candycanecazoo.packapunch.map;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Kinds of feature block whose position the map indexes. Their config lives in their BlockEntity. */
public enum FeatureKind implements StringRepresentable {
    SPAWN("spawn"),
    POWER_SWITCH("power_switch"),
    WALL_BUY("wall_buy"),
    PERK_MACHINE("perk_machine"),
    MYSTERY_BOX("mystery_box"),
    PAP_MACHINE("pap_machine");

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
