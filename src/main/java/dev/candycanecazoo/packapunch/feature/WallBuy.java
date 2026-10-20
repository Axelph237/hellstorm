package dev.candycanecazoo.packapunch.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.util.Codecs;
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

    /** {@code ammoCost} and {@code papAmmoCost} are what a refill costs on a plain and a Pack-a-Punched copy of the gun. */
    public record Config(ItemSpec item, int cost, Repurchase repurchase, int ammoCost, int papAmmoCost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemSpec.CODEC.fieldOf("item").forGetter(Config::item),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("cost").forGetter(Config::cost),
                Codecs.strictOptional(Repurchase.CODEC, "repurchase").forGetter(c -> Optional.of(c.repurchase())),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "ammo_cost").forGetter(c -> Optional.of(c.ammoCost())),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "pap_ammo_cost", DEFAULT_PAP_AMMO_COST).forGetter(Config::papAmmoCost)
        ).apply(i, (item, cost, repurchase, ammo, papAmmo) ->
                new Config(item, cost, repurchase.orElseGet(() -> Repurchase.defaultFor(item)), ammo.orElse(cost / 2), papAmmo)));

        public static final Config DEFAULT = forItem(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47")), 1200, Repurchase.REFILL_AMMO);

        /** A new item at {@code cost}: refills cost half the price, upgraded refills the default. */
        public static Config forItem(ItemSpec item, int cost, Repurchase repurchase) {
            return new Config(item, cost, repurchase, cost / 2, DEFAULT_PAP_AMMO_COST);
        }

        /** Refilling ammo only makes sense for a gun; other items top up instead. */
        public Repurchase effectiveRepurchase() {
            return repurchase == Repurchase.REFILL_AMMO && !(item instanceof ItemSpec.Gun) ? Repurchase.TOP_UP : repurchase;
        }
    }

    /** What the player already carries of the item: how many, and whether a gun is Pack-a-Punched. */
    public record Owned(int count, boolean punched) {}

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
            return new Action.Refill(have.punched() ? config.papAmmoCost() : config.ammoCost());
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
