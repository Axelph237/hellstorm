package dev.candycanecazoo.packapunch.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue OUTLINE_RANGE;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_POPUPS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tools");
        OUTLINE_RANGE = builder
                .comment("Zones within this many blocks are outlined while a map tool is held.")
                .defineInRange("outline_range", 64, 8, 256);
        builder.pop();
        builder.push("hud");
        HUD_SCALE = builder
                .comment("Size of the in-game HUD (round, points, perks), on top of Minecraft's GUI scale. Prompts and notices keep their size.")
                .defineInRange("scale", 1.0, 0.5, 2.0);
        SHOW_POPUPS = builder
                .comment("Show the +N / -N popups next to your points when they change.")
                .define("show_popups", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
