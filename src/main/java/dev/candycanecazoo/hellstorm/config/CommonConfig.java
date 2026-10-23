package dev.candycanecazoo.hellstorm.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings both sides read at startup, from {@code config/hellstorm-common.toml}. Changing one takes a restart: the
 * client picks its zombie renderer once, as the game loads.
 */
public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DISMEMBERMENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("zombies");
        DISMEMBERMENT = builder
                .comment("Shot zombies lose arms and legs, and crawl without a leg. Off, they keep their limbs, and the client keeps Minecraft's own zombie renderer (for mods or resource packs that replace it). A zombie tagged as a crawler still has a crawler's hitbox and speed, but is drawn standing.")
                .define("dismemberment", true);
        builder.pop();
        SPEC = builder.build();
    }

    private CommonConfig() {}
}
