package dev.candycanecazoo.packapunch.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-world server config (serverconfig/packapunch-server.toml), synced to clients. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DETECT_MAX_VOLUME;
    public static final ForgeConfigSpec.DoubleValue TOOL_REACH;
    public static final ForgeConfigSpec.IntValue MAX_PASSAGE_GAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tools");
        DETECT_MAX_VOLUME = builder
                .comment("Largest room, in blocks, that /pap zone detect will fill before reporting the room is not closed.")
                .defineInRange("detect_max_volume", 32768, 64, 1_000_000);
        TOOL_REACH = builder
                .comment("How far, in blocks, map tools can select and highlight.")
                .defineInRange("reach", 32.0, 4.0, 128.0);
        builder.pop();
        builder.push("map");
        MAX_PASSAGE_GAP = builder
                .comment("Two zones count as connected when a mob could walk between them through at most this many cells that belong to no zone (a doorway, an arch, an open door).")
                .defineInRange("max_passage_gap", 2, 0, 8);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
