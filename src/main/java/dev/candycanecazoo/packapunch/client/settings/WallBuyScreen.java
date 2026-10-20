package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.WallBuy;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What a wall-buy sells (a TacZ gun found by name, or the item in hand), its price, and what buying it again does. */
final class WallBuyScreen extends SettingsScreen<WallBuy.Config> {
    private static final int RESULTS = 3;

    private final List<OpenSettingsPacket.Choice> guns;
    private final Optional<OpenSettingsPacket.Held> held;
    private final List<Button> results = new ArrayList<>();
    private final List<OpenSettingsPacket.Choice> matches = new ArrayList<>();
    private final List<EditBox> ammoBoxes = new ArrayList<>();
    private ItemSpec item;
    private Component itemName;
    private WallBuy.Repurchase repurchase;
    private String cost;
    private String ammoCost;
    private String papAmmoCost;
    private String search = "";

    WallBuyScreen(WallBuyBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Wall-buy"), entity);
        WallBuy.Config config = entity.config();
        guns = packet.choices();
        held = packet.held();
        item = config.item();
        itemName = entity.display().isEmpty() ? Component.literal(item.describe()) : entity.display().getHoverName();
        repurchase = config.effectiveRepurchase();
        cost = String.valueOf(config.cost());
        ammoCost = String.valueOf(config.ammoCost());
        papAmmoCost = String.valueOf(config.papAmmoCost());
    }

    @Override
    protected void rows() {
        text(Component.literal("Sells"), () -> itemName);
        held.ifPresent(h -> button(Component.empty(), Component.literal("Sell held: ").append(h.name()), () -> pick(h.item(), h.name())));
        search(Component.literal("Find gun"), search, v -> {
            search = v;
            refreshResults();
        });
        results.clear();
        for (int i = 0; i < RESULTS; i++) {
            int index = i;
            results.add(button(Component.empty(), Component.empty(), () -> pick(ItemSpec.Gun.of(matches.get(index).id()), matches.get(index).name())));
        }
        refreshResults();
        number(Component.literal("Cost"), cost, Component.empty(), v -> cost = v);
        choice(Component.literal("Buying again"), allowed(), repurchase, WallBuyScreen::name, v -> {
            repurchase = v;
            ammoBoxes.forEach(box -> box.active = refills());
        });
        ammoBoxes.clear();
        ammoBoxes.add(number(Component.literal("Ammo cost"), ammoCost, Component.empty(), v -> ammoCost = v));
        ammoBoxes.add(number(Component.literal("Upgraded ammo"), papAmmoCost, Component.empty(), v -> papAmmoCost = v));
        ammoBoxes.forEach(box -> box.active = refills());
    }

    private void refreshResults() {
        String query = search.toLowerCase(Locale.ROOT);
        matches.clear();
        guns.stream().filter(g -> g.id().toString().contains(query) || g.name().getString().toLowerCase(Locale.ROOT).contains(query))
                .limit(RESULTS).forEach(matches::add);
        for (int i = 0; i < results.size(); i++) {
            Button result = results.get(i);
            result.visible = i < matches.size();
            if (result.visible) {
                result.setMessage(matches.get(i).name());
            }
        }
    }

    private void pick(ItemSpec chosen, Component name) {
        item = chosen;
        itemName = name;
        repurchase = WallBuy.Repurchase.defaultFor(chosen);
        rebuildWidgets();
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
                .map(papAmmo -> new WallBuy.Config(item, price, repurchase, ammo, papAmmo))));
    }
}
