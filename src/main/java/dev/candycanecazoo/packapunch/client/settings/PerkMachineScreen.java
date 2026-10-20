package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PerkMachineScreen extends SettingsScreen<PerkMachineBlock.Config> {
    private final List<Optional<ResourceLocation>> perks = new ArrayList<>();
    private final Map<ResourceLocation, Component> names;
    private Optional<ResourceLocation> perk;

    PerkMachineScreen(PerkMachineBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Perk machine"), entity);
        perk = entity.config().perk();
        names = Choices.names(packet.choices());
        perks.add(Optional.empty());
        Choices.ids(packet.choices(), perk.orElse(null)).forEach(id -> perks.add(Optional.of(id)));
    }

    @Override
    protected void rows() {
        choice(Component.literal("Perk"), perks, perk,
                p -> p.map(id -> names.getOrDefault(id, Component.literal(id + " (not loaded)"))).orElse(Component.literal("None")), v -> perk = v);
    }

    @Override
    protected DataResult<PerkMachineBlock.Config> settings() {
        return DataResult.success(new PerkMachineBlock.Config(perk));
    }
}
