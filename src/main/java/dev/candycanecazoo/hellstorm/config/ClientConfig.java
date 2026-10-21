package dev.candycanecazoo.hellstorm.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue OUTLINE_RANGE;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_POPUPS;
    public static final ForgeConfigSpec.EnumValue<PromptStyle> PROMPT_STYLE;

    /** Where "AK-47 [1200]" shows: floating at the thing you're looking at, or under the crosshair. */
    public enum PromptStyle { WORLD, HUD }

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
        PROMPT_STYLE = builder
                .comment("Where the name and price of what you're looking at shows: WORLD floats it at the thing itself, HUD puts it under the crosshair.")
                .defineEnum("prompt_style", PromptStyle.WORLD);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
