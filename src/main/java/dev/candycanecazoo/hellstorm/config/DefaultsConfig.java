package dev.candycanecazoo.hellstorm.config;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Per-world {@code serverconfig/hellstorm-defaults.toml}: what wall-buys and vending machines start with for each item
 * they can sell ({@link PurchaseDefaults}). Read again whenever the file changes.
 */
public final class DefaultsConfig {
    public static final String FILE = Hellstorm.MOD_ID + "-defaults.toml";
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUNS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEMS;

    private static List<? extends String> readGuns;
    private static List<? extends String> readItems;
    private static PurchaseDefaults current = PurchaseDefaults.BUILT_IN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "What a wall-buy or vending machine starts with when it's set to sell an item (\"Sell held\" on its settings screen, or a new wall-buy's gun).",
                "Each entry: \"<id> = <cost>, <buying again>, <ammo cost>, <upgraded ammo cost>\".",
                "Buying again is refill_ammo (guns only), top_up, duplicate or deny. The ammo costs are what a refill costs on a plain and an upgraded gun;",
                "they can be left off (\"<id> = <cost>, <buying again>\"), for half the cost and 4500. Whether it needs the power is the machine's own:",
                "vending machines do, wall-buys don't. An item not listed keeps the machine's cost. Entries that can't be read are left out, with a warning in the log.");
        builder.push("wall_buys");
        GUNS = builder
                .comment("TacZ guns, by gun id (the one /hs give takes), from any gun pack.")
                .defineListAllowEmpty(List.of("guns"), () -> PurchaseDefaults.DEFAULT_GUNS, o -> o instanceof String);
        ITEMS = builder
                .comment("Any other item, by item id. A drink whose perk the buyer already has is refused whatever this says.")
                .defineListAllowEmpty(List.of("items"), () -> PurchaseDefaults.DEFAULT_ITEMS, o -> o instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private DefaultsConfig() {}

    /** The defaults as the file has them, or the built-in ones before a world's config has loaded. */
    public static synchronized PurchaseDefaults current() {
        if (!SPEC.isLoaded()) {
            return PurchaseDefaults.BUILT_IN;
        }
        List<? extends String> guns = GUNS.get();
        List<? extends String> items = ITEMS.get();
        if (guns != readGuns || items != readItems) {
            PurchaseDefaults.Parsed parsed = PurchaseDefaults.parse(List.copyOf(guns), List.copyOf(items));
            parsed.problems().forEach(problem -> Hellstorm.LOG.warn("{}: {}", FILE, problem));
            current = parsed.defaults();
            readGuns = guns;
            readItems = items;
        }
        return current;
    }
}
