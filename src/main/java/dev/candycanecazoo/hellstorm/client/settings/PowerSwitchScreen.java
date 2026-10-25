package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.feature.PowerSwitchBlock;
import net.minecraft.network.chat.Component;

final class PowerSwitchScreen extends SettingsScreen<PowerSwitchBlock.Config> {
    private String cost;

    PowerSwitchScreen(PowerSwitchBlock.Entity entity) {
        super(Component.literal("Power switch"), entity);
        cost = String.valueOf(entity.config().cost());
    }

    @Override
    protected void rows() {
        number(Component.literal("Cost"), cost, Component.literal("0 is free"), 50, v -> cost = v);
    }

    @Override
    protected DataResult<PowerSwitchBlock.Config> settings() {
        return cost(cost, "Cost").map(PowerSwitchBlock.Config::new);
    }
}
