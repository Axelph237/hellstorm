package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.data.PurchaseDefault;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Optional;

/** What a wall-buy sells and what buying it again does. Pure rules; {@link WallBuyBlock} applies them to a player. */
public final class WallBuy {
    public static final int DEFAULT_PAP_AMMO_COST = 4500;

    public enum Repurchase implements StringRepresentable {
        REFILL_AMMO, TOP_UP, DUPLICATE, DENY;

        public static final Codec<Repurchase> CODEC = StringRepresentable.fromEnum(Repurchase::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Repurchase defaultFor(ItemSpec item, boolean stackable) {
            if (item instanceof ItemSpec.Gun) {
                return REFILL_AMMO;
            }
            return stackable ? TOP_UP : DENY;
        }

        public static Repurchase defaultFor(ItemSpec item) {
            return defaultFor(item, item instanceof ItemSpec.Plain plain && stackable(plain.item()));
        }

        private static boolean stackable(ResourceLocation id) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item != null && item.getMaxStackSize() > 1;
        }
    }

    /**
     * {@code ammoCost} and {@code papAmmoCost} are what a refill costs on a plain and an upgraded copy of the gun.
     * {@code requiresPower} keeps it from selling until the power is on, as vending machines do by default.
     */
    public record Config(ItemSpec item, int cost, Repurchase repurchase, int ammoCost, int papAmmoCost, boolean requiresPower) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemSpec.CODEC.fieldOf("item").forGetter(Config::item),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("cost").forGetter(Config::cost),
                Codecs.strictOptional(Repurchase.CODEC, "repurchase").forGetter(c -> Optional.of(c.repurchase())),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "ammo_cost").forGetter(c -> Optional.of(c.ammoCost())),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "pap_ammo_cost", DEFAULT_PAP_AMMO_COST).forGetter(Config::papAmmoCost),
                Codecs.strictOptional(Codec.BOOL, "requires_power", false).forGetter(Config::requiresPower)
        ).apply(i, (item, cost, repurchase, ammo, papAmmo, power) ->
                new Config(item, cost, repurchase.orElseGet(() -> Repurchase.defaultFor(item)), ammo.orElse(cost / 2), papAmmo, power)));

        public static final Config DEFAULT = forItem(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47")), 1200, Repurchase.REFILL_AMMO);

        /** A new item at {@code cost}: refills cost half the price, upgraded refills the default, and no power needed. */
        public static Config forItem(ItemSpec item, int cost, Repurchase repurchase) {
            return new Config(item, cost, repurchase, cost / 2, DEFAULT_PAP_AMMO_COST, false);
        }

        /** Sells {@code item} at its listed price, buying again and ammo costs, still needing the power or not as before. */
        public Config selling(ItemSpec item, PurchaseDefault defaults) {
            return new Config(item, defaults.cost(), defaults.repurchase(), defaults.ammoCost(), defaults.papAmmoCost(), requiresPower);
        }

        public Config withRequiresPower(boolean power) {
            return new Config(item, cost, repurchase, ammoCost, papAmmoCost, power);
        }

        /** Refilling ammo only makes sense for a gun; other items top up instead. */
        public Repurchase effectiveRepurchase() {
            return repurchase == Repurchase.REFILL_AMMO && !(item instanceof ItemSpec.Gun) ? Repurchase.TOP_UP : repurchase;
        }
    }

    /** Why a wall-buy can't sell this: an unknown gun or item, or refilling ammo on something that isn't a gun. */
    public static Optional<String> problem(Config config) {
        if (config.item() instanceof ItemSpec.Gun gun && !TaczBridge.gunExists(gun.gunId())) {
            return Optional.of("Unknown TacZ gun: " + gun.gunId());
        }
        if (config.item() instanceof ItemSpec.Plain plain && !ForgeRegistries.ITEMS.containsKey(plain.item())) {
            return Optional.of("Unknown item: " + plain.item());
        }
        if (config.repurchase() == Repurchase.REFILL_AMMO && !(config.item() instanceof ItemSpec.Gun)) {
            return Optional.of("Only TacZ guns refill ammo; use top_up, duplicate, or deny");
        }
        return Optional.empty();
    }

    /** What the player already carries of the item: how many, whether a gun is upgraded, and whether a refill would add ammo. */
    public record Owned(int count, boolean punched, Ammo ammo) {}

    /** Whether a refill would give the gun anything: it has room, it is full, or its ammo never runs out. ROOM for other items. */
    public enum Ammo {
        ROOM, FULL, UNLIMITED
    }

    public sealed interface Action {
        int cost();

        record Buy(int cost) implements Action {}

        record Refill(int cost) implements Action {}

        /** Adds {@code missing} to fill back up to the spec's count. */
        record TopUp(int cost, int missing) implements Action {}

        record Deny(String reason) implements Action {
            public int cost() {
                return 0;
            }
        }
    }

    private WallBuy() {}

    public static Action decide(Config config, Optional<Owned> owned) {
        if (owned.isEmpty()) {
            return new Action.Buy(config.cost());
        }
        Owned have = owned.get();
        Repurchase repurchase = config.effectiveRepurchase();
        if (repurchase == Repurchase.REFILL_AMMO) {
            return switch (have.ammo()) {
                case FULL -> new Action.Deny("That gun's ammo is full");
                case UNLIMITED -> new Action.Deny("That gun has unlimited ammo");
                case ROOM -> new Action.Refill(have.punched() ? config.papAmmoCost() : config.ammoCost());
            };
        }
        if (repurchase == Repurchase.DUPLICATE) {
            return new Action.Buy(config.cost());
        }
        if (repurchase == Repurchase.TOP_UP) {
            int missing = count(config.item()) - have.count();
            return missing > 0 ? new Action.TopUp(config.cost(), missing) : new Action.Deny("You already have a full stack");
        }
        return new Action.Deny("You already have this");
    }

    static int count(ItemSpec item) {
        return item instanceof ItemSpec.Plain plain ? plain.count() : 1;
    }
}
