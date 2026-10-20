package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PerkMachineScreen extends SettingsScreen<PerkMachineBlock.Config> {
    private final List<Optional<ResourceLocation>> perks = new ArrayList<>();
    private final Map<ResourceLocation, OpenSettingsPacket.Choice> choices;
    private Optional<ResourceLocation> perk;
    private String cost;
    private EditBox costBox;

    PerkMachineScreen(PerkMachineBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Perk machine"), entity);
        perk = entity.config().perk();
        cost = entity.config().cost().map(String::valueOf).orElse("");
        choices = Choices.byId(packet.choices());
        perks.add(Optional.empty());
        Choices.ids(packet.choices(), perk.orElse(null)).forEach(id -> perks.add(Optional.of(id)));
    }

    @Override
    protected void rows() {
        choice(Component.literal("Perk"), perks, perk, this::name, v -> {
            perk = v;
            costBox.setHint(defaultHint());
        });
        costBox = number(Component.literal("Cost"), cost, defaultHint(), v -> cost = v);
    }

    private Component name(Optional<ResourceLocation> perk) {
        return perk.map(id -> Optional.ofNullable(choices.get(id)).map(OpenSettingsPacket.Choice::name)
                .orElse(Component.literal(id + " (not loaded)"))).orElse(Component.literal("None"));
    }

    /** A blank cost sells the perk at its own cost. */
    private Component defaultHint() {
        return perk.map(choices::get).map(c -> Component.literal("Perk's cost (" + c.cost() + ")")).orElse(Component.literal("Perk's cost"));
    }

    @Override
    protected DataResult<PerkMachineBlock.Config> settings() {
        return DataResult.success(new PerkMachineBlock.Config(perk, optionalCost(cost)));
    }
}
