package dev.candycanecazoo.hellstorm.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-world server config (serverconfig/hellstorm-server.toml), synced to clients. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DETECT_MAX_VOLUME;
    public static final ForgeConfigSpec.DoubleValue TOOL_REACH;
    public static final ForgeConfigSpec.IntValue MAX_PASSAGE_GAP;
    public static final ForgeConfigSpec.IntValue EXPORT_MARGIN;
    public static final GameSettingsConfig GAME;
    public static final ForgeConfigSpec.BooleanValue SCRIPTING_ENABLED;
    public static final ForgeConfigSpec.IntValue SCRIPT_PERMISSION_LEVEL;
    public static final ForgeConfigSpec.LongValue SCRIPT_INSTRUCTION_BUDGET;
    public static final ForgeConfigSpec.IntValue SCRIPT_MAX_CONSECUTIVE_ERRORS;
    public static final ForgeConfigSpec.IntValue SCRIPT_MAX_STRING_LENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tools");
        DETECT_MAX_VOLUME = builder
                .comment("Largest room, in blocks, that /hs zones detect will fill before reporting the room is not closed.")
                .defineInRange("detect_max_volume", 32768, 64, 1_000_000);
        TOOL_REACH = builder
                .comment("How far, in blocks, map tools can select and highlight.")
                .defineInRange("reach", 32.0, 4.0, 128.0);
        builder.pop();
        builder.push("map");
        MAX_PASSAGE_GAP = builder
                .comment("Two rooms count as connected when a mob could walk between them in at most this many steps outside any room (a doorway, an arch, an open door).")
                .defineInRange("max_passage_gap", 2, 0, 8);
        builder.pop();
        builder.push("export");
        EXPORT_MARGIN = builder
                .comment("/hs map export without a Zone Wand selection saves the map's rooms, machines, windows, and doors plus this many blocks around them, for walls, floors, and spawn areas.")
                .defineInRange("margin", 6, 0, 64);
        builder.pop();
        GAME = new GameSettingsConfig(builder);
        // Not part of GameSettings on purpose: a map's settings override must never raise its own script permissions.
        builder.push("scripting");
        SCRIPTING_ENABLED = builder.comment("Run datapack Lua scripts and event defs in games.").define("enabled", true);
        SCRIPT_PERMISSION_LEVEL = builder
                .comment("Permission level script commands run at. 2 matches datapack functions.")
                .defineInRange("command_permission_level", 2, 0, 4);
        SCRIPT_INSTRUCTION_BUDGET = builder
                .comment("Lua instructions one listener call (or one script load) may run before it is stopped.")
                .defineInRange("instruction_budget", 1_000_000L, 1_000L, 1_000_000_000L);
        SCRIPT_MAX_CONSECUTIVE_ERRORS = builder
                .comment("A listener that fails this many times in a row is disabled for the rest of the game.")
                .defineInRange("max_consecutive_errors", 5, 1, 1000);
        SCRIPT_MAX_STRING_LENGTH = builder
                .comment("Longest string string.rep may build.")
                .defineInRange("max_string_length", 1_000_000, 1_000, 100_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
