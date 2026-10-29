package dev.candycanecazoo.hellstorm.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue OUTLINE_RANGE;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue SHOW_POPUPS;
    public static final ForgeConfigSpec.EnumValue<PromptStyle> PROMPT_STYLE;
    public static final ForgeConfigSpec.EnumValue<PlayerListPosition> PLAYER_LIST;

    /** Where "AK-47 [1200]" shows: floating at the thing you're looking at, or under the crosshair. */
    public enum PromptStyle { WORLD, HUD }

    /** Where the HUD's rows of players sit: a side of the screen, and its top, middle or bottom. */
    public enum PlayerListPosition {
        TOP_LEFT(false, Edge.TOP), CENTER_LEFT(false, Edge.CENTER), BOTTOM_LEFT(false, Edge.BOTTOM),
        TOP_RIGHT(true, Edge.TOP), CENTER_RIGHT(true, Edge.CENTER), BOTTOM_RIGHT(true, Edge.BOTTOM);

        public enum Edge { TOP, CENTER, BOTTOM }

        public final boolean right;
        public final Edge edge;

        PlayerListPosition(boolean right, Edge edge) {
            this.right = right;
            this.edge = edge;
        }

        /** The top of a list {@code block} tall on a screen {@code height} tall, kept below {@code clearTop} at the top. */
        public int top(int height, int block, int margin, int clearTop) {
            return switch (edge) {
                case TOP -> Math.max(margin, clearTop);
                case CENTER -> (height - block) / 2;
                case BOTTOM -> height - margin - block;
            };
        }
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tools");
        OUTLINE_RANGE = builder
                .comment("Rooms, doors, and windows within this many blocks are outlined while a map tool is held.")
                .defineInRange("outline_range", 64, 8, 256);
        builder.pop();
        builder.push("hud");
        HUD_SCALE = builder
                .comment("Size of the in-game HUD (round, players, power-up timers), on top of Minecraft's GUI scale. Prompts and notices keep their size.")
                .defineInRange("scale", 1.0, 0.5, 2.0);
        SHOW_POPUPS = builder
                .comment("Show the +N / -N popups next to your points in the player list when they change.")
                .define("show_popups", true);
        PROMPT_STYLE = builder
                .comment("Where the name and price of what you're looking at shows: WORLD floats it at the thing itself, HUD puts it under the crosshair.")
                .defineEnum("prompt_style", PromptStyle.WORLD);
        PLAYER_LIST = builder
                .comment("Where the rows of players (face, name, points) sit: TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT, TOP_RIGHT, CENTER_RIGHT or BOTTOM_RIGHT.",
                        "On the left a row reads face, name, points; on the right points, name, face.")
                .defineEnum("player_list", PlayerListPosition.CENTER_RIGHT);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
