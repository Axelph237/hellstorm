package dev.candycanecazoo.packapunch.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue OUTLINE_RANGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tools");
        OUTLINE_RANGE = builder
                .comment("Zones within this many blocks are outlined while a map tool is held.")
                .defineInRange("outline_range", 64, 8, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
