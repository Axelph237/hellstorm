package dev.candycanecazoo.hellstorm.config;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a wall-buy or vending machine starts with when it's set to sell an item: its price, what buying it again does,
 * and what refilling a plain and an upgraded copy cost. Guns are listed by TacZ gun id, anything else by item id. Each
 * entry is a line {@code "<id> = <cost>, <buying again>[, <ammo cost>, <upgraded ammo cost>]"}, as the config file
 * holds them; left out, the ammo costs are half the price and {@link WallBuy#DEFAULT_PAP_AMMO_COST}.
 */
public record PurchaseDefaults(Map<ResourceLocation, Purchase> guns, Map<ResourceLocation, Purchase> items) {
    public record Purchase(int cost, WallBuy.Repurchase repurchase, int ammoCost, int papAmmoCost) {}

    /** The defaults, and a line for each entry that couldn't be read (and was left out). */
    public record Parsed(PurchaseDefaults defaults, List<String> problems) {}

    /** TacZ's default gun pack, by type: pistols, SMGs, rifles, shotguns, snipers, machine guns, launchers. */
    public static final List<String> DEFAULT_GUNS = List.of(
            "tacz:taurus943 = 400, refill_ammo, 200, 4500",
            "tacz:glock_17 = 500, refill_ammo, 250, 4500",
            "tacz:m9a4 = 500, refill_ammo, 250, 4500",
            "tacz:m1911 = 500, refill_ammo, 250, 4500",
            "tacz:cz75 = 500, refill_ammo, 250, 4500",
            "tacz:p320 = 600, refill_ammo, 300, 4500",
            "tacz:hk_mk23 = 600, refill_ammo, 300, 4500",
            "tacz:b93r = 750, refill_ammo, 400, 4500",
            "tacz:rhino357 = 750, refill_ammo, 400, 4500",
            "tacz:deagle = 1000, refill_ammo, 500, 4500",
            "tacz:timeless50 = 1000, refill_ammo, 500, 4500",
            "tacz:lonetrail = 1000, refill_ammo, 500, 4500",
            "tacz:taurus500 = 1200, refill_ammo, 600, 4500",
            "tacz:deagle_golden = 1500, refill_ammo, 750, 4500",
            "tacz:uzi = 1000, refill_ammo, 500, 4500",
            "tacz:hk_mp5a5 = 1000, refill_ammo, 500, 4500",
            "tacz:ump45 = 1200, refill_ammo, 600, 4500",
            "tacz:vector45 = 1200, refill_ammo, 600, 4500",
            "tacz:p90 = 1300, refill_ammo, 650, 4500",
            "tacz:sks_tactical = 1000, refill_ammo, 500, 4500",
            "tacz:m16a1 = 1200, refill_ammo, 600, 4500",
            "tacz:m16a4 = 1200, refill_ammo, 600, 4500",
            "tacz:ak47 = 1200, refill_ammo, 600, 4500",
            "tacz:type_81 = 1200, refill_ammo, 600, 4500",
            "tacz:qbz_95 = 1300, refill_ammo, 650, 4500",
            "tacz:qbz_191 = 1400, refill_ammo, 700, 4500",
            "tacz:m4a1 = 1400, refill_ammo, 700, 4500",
            "tacz:g36k = 1400, refill_ammo, 700, 4500",
            "tacz:spr15hb = 1400, refill_ammo, 700, 4500",
            "tacz:aug = 1500, refill_ammo, 750, 4500",
            "tacz:scar_l = 1500, refill_ammo, 750, 4500",
            "tacz:fn_fal = 1500, refill_ammo, 750, 4500",
            "tacz:hk_g3 = 1500, refill_ammo, 750, 4500",
            "tacz:mk14 = 1500, refill_ammo, 750, 4500",
            "tacz:hk416d = 1600, refill_ammo, 800, 4500",
            "tacz:scar_h = 1600, refill_ammo, 800, 4500",
            "tacz:db_short = 1000, refill_ammo, 500, 4500",
            "tacz:db_long = 1000, refill_ammo, 500, 4500",
            "tacz:m870 = 1500, refill_ammo, 750, 4500",
            "tacz:m1014 = 1500, refill_ammo, 750, 4500",
            "tacz:spas_12 = 2000, refill_ammo, 1000, 4500",
            "tacz:aa12 = 2500, refill_ammo, 1250, 4500",
            "tacz:springfield1873 = 1000, refill_ammo, 500, 4500",
            "tacz:kar98 = 1000, refill_ammo, 500, 4500",
            "tacz:m700 = 1500, refill_ammo, 750, 4500",
            "tacz:ai_awp = 2000, refill_ammo, 1000, 4500",
            "tacz:m107 = 2500, refill_ammo, 1250, 4500",
            "tacz:m95 = 3000, refill_ammo, 1500, 4500",
            "tacz:rpk = 2000, refill_ammo, 1000, 4500",
            "tacz:m249 = 2500, refill_ammo, 1250, 4500",
            "tacz:fn_evolys = 3000, refill_ammo, 1500, 4500",
            "tacz:minigun = 5000, refill_ammo, 2500, 4500",
            "tacz:m320 = 2500, refill_ammo, 1250, 4500",
            "tacz:rpg7 = 3000, refill_ammo, 1500, 4500");

    /** The perk cans: one at a time. A perk the buyer already has is refused whatever this says. */
    public static final List<String> DEFAULT_ITEMS = List.of(
            "hellstorm:vigoraid = 2500, deny, 0, 0",
            "hellstorm:load_up = 3000, deny, 0, 0",
            "hellstorm:saints_brew = 1500, deny, 0, 0");

    public static final PurchaseDefaults BUILT_IN = parse(DEFAULT_GUNS, DEFAULT_ITEMS).defaults();

    public Optional<Purchase> of(ItemSpec item) {
        if (item instanceof ItemSpec.Gun gun) {
            return Optional.ofNullable(guns.get(gun.gunId()));
        }
        return item instanceof ItemSpec.Plain plain ? Optional.ofNullable(items.get(plain.item())) : Optional.empty();
    }

    public static Parsed parse(List<String> guns, List<String> items) {
        List<String> problems = new ArrayList<>();
        return new Parsed(new PurchaseDefaults(entries(guns, "guns", true, problems), entries(items, "items", false, problems)), List.copyOf(problems));
    }

    private static Map<ResourceLocation, Purchase> entries(List<String> lines, String list, boolean guns, List<String> problems) {
        Map<ResourceLocation, Purchase> entries = new LinkedHashMap<>();
        for (String line : lines) {
            try {
                Map.Entry<ResourceLocation, Purchase> entry = entry(line);
                if (!guns && entry.getValue().repurchase() == WallBuy.Repurchase.REFILL_AMMO) {
                    throw new IllegalArgumentException("only guns refill ammo; use top_up, duplicate or deny");
                }
                if (entries.put(entry.getKey(), entry.getValue()) != null) {
                    problems.add(list + ": " + entry.getKey() + " is listed twice; the last one counts");
                }
            } catch (IllegalArgumentException e) {
                problems.add(list + ": \"" + line + "\": " + e.getMessage());
            }
        }
        return Map.copyOf(entries);
    }

    static Map.Entry<ResourceLocation, Purchase> entry(String line) {
        int equals = line.indexOf('=');
        if (equals < 0) {
            throw new IllegalArgumentException("expected \"<id> = <cost>, <buying again>\"");
        }
        ResourceLocation id = ResourceLocation.tryParse(line.substring(0, equals).trim());
        if (id == null) {
            throw new IllegalArgumentException("not an id: " + line.substring(0, equals).trim());
        }
        String[] parts = line.substring(equals + 1).split(",");
        if (parts.length != 2 && parts.length != 4) {
            throw new IllegalArgumentException("expected a cost and buying again, then optionally the ammo cost and upgraded ammo cost");
        }
        int cost = amount(parts[0], "cost");
        WallBuy.Repurchase repurchase = repurchase(parts[1].trim());
        int ammo = parts.length == 4 ? amount(parts[2], "ammo cost") : cost / 2;
        int papAmmo = parts.length == 4 ? amount(parts[3], "upgraded ammo cost") : WallBuy.DEFAULT_PAP_AMMO_COST;
        return Map.entry(id, new Purchase(cost, repurchase, ammo, papAmmo));
    }

    private static int amount(String text, String what) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Reported below, the same as a negative amount.
        }
        throw new IllegalArgumentException("the " + what + " must be a whole number of 0 or more, not " + text.trim());
    }

    private static WallBuy.Repurchase repurchase(String name) {
        for (WallBuy.Repurchase repurchase : WallBuy.Repurchase.values()) {
            if (repurchase.getSerializedName().equals(name.toLowerCase(Locale.ROOT))) {
                return repurchase;
            }
        }
        throw new IllegalArgumentException("buying again must be refill_ammo, top_up, duplicate or deny, not " + name);
    }
}
