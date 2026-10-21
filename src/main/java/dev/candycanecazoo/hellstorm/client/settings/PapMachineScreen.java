package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;

final class PapMachineScreen extends SettingsScreen<PapMachineBlock.Config> {
    private final int defaultCost;
    private String cost;

    PapMachineScreen(PapMachineBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.translatable(PapMachineBlock.NAME), entity);
        defaultCost = packet.defaultCost();
        cost = entity.config().cost().map(String::valueOf).orElse("");
    }

    @Override
    protected void rows() {
        number(Component.literal("Cost"), cost, Component.literal("Default (" + defaultCost + ")"), v -> cost = v);
    }

    @Override
    protected DataResult<PapMachineBlock.Config> settings() {
        return DataResult.success(new PapMachineBlock.Config(optionalCost(cost)));
    }
}
