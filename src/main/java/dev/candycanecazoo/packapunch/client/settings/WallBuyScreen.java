package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.WallBuy;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a wall-buy or perk machine sells (the item in hand when the screen opened), its price, whether it needs power, and
 * what buying it again does. The ammo costs only show when buying again refills ammo.
 */
final class WallBuyScreen extends SettingsScreen<WallBuy.Config> {
    private final Optional<OpenSettingsPacket.Held> held;
    private ItemSpec item;
    private Component itemName;
    private WallBuy.Repurchase repurchase;
    private String cost;
    private String ammoCost;
    private String papAmmoCost;
    private boolean requiresPower;

    WallBuyScreen(WallBuyBlock.Entity entity, OpenSettingsPacket packet) {
        super(entity.getBlockState().getBlock().getName(), entity);
        WallBuy.Config config = entity.config();
        held = packet.held();
        item = config.item();
        itemName = entity.display().isEmpty() ? Component.literal(item.describe()) : entity.display().getHoverName();
        repurchase = config.effectiveRepurchase();
        cost = String.valueOf(config.cost());
        ammoCost = String.valueOf(config.ammoCost());
        papAmmoCost = String.valueOf(config.papAmmoCost());
        requiresPower = config.requiresPower();
    }

    @Override
    protected void rows() {
        text(Component.literal("Sells"), () -> itemName);
        held.ifPresentOrElse(
                h -> button(Component.empty(), Component.literal("Sell held: ").append(h.name()), () -> sell(h)),
                () -> text(Component.empty(), () -> Component.literal("Hold an item to sell it")));
        number(Component.literal("Cost"), cost, Component.empty(), v -> cost = v);
        choice(Component.literal("Needs power"), List.of(true, false), requiresPower,
                v -> Component.literal(v ? "Yes" : "No"), v -> requiresPower = v);
        choice(Component.literal("Buying again"), allowed(), repurchase, WallBuyScreen::name, v -> {
            repurchase = v;
            relayout();
        });
        if (refills()) {
            number(Component.literal("Ammo cost"), ammoCost, Component.empty(), v -> ammoCost = v);
            number(Component.literal("Upgraded ammo"), papAmmoCost, Component.empty(), v -> papAmmoCost = v);
        }
    }

    private void sell(OpenSettingsPacket.Held held) {
        item = held.item();
        itemName = held.name();
        repurchase = WallBuy.Repurchase.defaultFor(item);
        relayout();
    }

    private List<WallBuy.Repurchase> allowed() {
        return Arrays.stream(WallBuy.Repurchase.values())
                .filter(r -> r != WallBuy.Repurchase.REFILL_AMMO || item instanceof ItemSpec.Gun).toList();
    }

    private boolean refills() {
        return repurchase == WallBuy.Repurchase.REFILL_AMMO;
    }

    private static Component name(WallBuy.Repurchase repurchase) {
        return Component.literal(switch (repurchase) {
            case REFILL_AMMO -> "Refill ammo";
            case TOP_UP -> "Top up the stack";
            case DUPLICATE -> "Sell another";
            case DENY -> "Refuse";
        });
    }

    @Override
    protected DataResult<WallBuy.Config> settings() {
        return cost(cost, "Cost").flatMap(price -> cost(ammoCost, "Ammo cost").flatMap(ammo -> cost(papAmmoCost, "Upgraded ammo")
                .map(papAmmo -> new WallBuy.Config(item, price, repurchase, ammo, papAmmo, requiresPower))));
    }
}
