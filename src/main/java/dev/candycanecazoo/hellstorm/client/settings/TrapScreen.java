package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import dev.candycanecazoo.hellstorm.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** A trap's name, price, whether it needs power, how long its redstone signal lasts and how strong it is, and its cooldown. */
final class TrapScreen extends SettingsScreen<TrapControllerBlock.Config> {
    private final int defaultCost;
    private String name;
    private String cost;
    private boolean requiresPower;
    private String activeTicks;
    private String cooldownTicks;
    private String signal;

    TrapScreen(TrapControllerBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Trap"), entity);
        TrapControllerBlock.Config config = entity.config();
        defaultCost = packet.defaultCost();
        name = config.name().orElse("");
        cost = config.cost().map(String::valueOf).orElse("");
        requiresPower = config.requiresPower();
        activeTicks = config.activeTicks().map(String::valueOf).orElse("");
        cooldownTicks = config.cooldownTicks().map(String::valueOf).orElse("");
        signal = String.valueOf(config.signalStrength());
    }

    @Override
    protected void rows() {
        text(Component.literal("Name"), name, TrapControllerBlock.Config.MAX_NAME, Component.literal("Trap"), v -> name = v);
        number(Component.literal("Cost"), cost, Component.literal("Default (" + defaultCost + ")"), 50, v -> cost = v);
        toggle(Component.literal("Needs power"), requiresPower, v -> requiresPower = v);
        number(Component.literal("Active (ticks)"), activeTicks, Component.literal("Default"), 20, v -> activeTicks = v);
        number(Component.literal("Cooldown (ticks)"), cooldownTicks, Component.literal("Default"), 20, v -> cooldownTicks = v);
        number(Component.literal("Signal (1-15)"), signal, Component.empty(), 1, v -> signal = v);
    }

    @Override
    protected DataResult<TrapControllerBlock.Config> settings() {
        return cost(signal, "Signal").flatMap(strength -> strength < 1 || strength > 15
                ? DataResult.error(() -> "Signal must be 1 to 15")
                : DataResult.success(new TrapControllerBlock.Config(
                        name.isBlank() ? Optional.empty() : Optional.of(name.trim()), optionalCost(cost), requiresPower, optionalCost(activeTicks),
                        optionalCost(cooldownTicks), strength)));
    }
}
