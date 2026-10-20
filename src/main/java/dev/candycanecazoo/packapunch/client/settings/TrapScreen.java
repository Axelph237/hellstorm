package dev.candycanecazoo.packapunch.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.packapunch.feature.TrapControllerBlock;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A trap's price, whether it needs power, how long its redstone signal lasts and how strong it is, and its cooldown. */
final class TrapScreen extends SettingsScreen<TrapControllerBlock.Config> {
    private final int defaultCost;
    private String cost;
    private boolean requiresPower;
    private String activeTicks;
    private String cooldownTicks;
    private String signal;

    TrapScreen(TrapControllerBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Trap"), entity);
        TrapControllerBlock.Config config = entity.config();
        defaultCost = packet.defaultCost();
        cost = config.cost().map(String::valueOf).orElse("");
        requiresPower = config.requiresPower();
        activeTicks = config.activeTicks().map(String::valueOf).orElse("");
        cooldownTicks = config.cooldownTicks().map(String::valueOf).orElse("");
        signal = String.valueOf(config.signalStrength());
    }

    @Override
    protected void rows() {
        number(Component.literal("Cost"), cost, Component.literal("Default (" + defaultCost + ")"), v -> cost = v);
        choice(Component.literal("Needs power"), List.of(true, false), requiresPower,
                v -> Component.literal(v ? "Yes" : "No"), v -> requiresPower = v);
        number(Component.literal("Active (ticks)"), activeTicks, Component.literal("Default"), v -> activeTicks = v);
        number(Component.literal("Cooldown (ticks)"), cooldownTicks, Component.literal("Default"), v -> cooldownTicks = v);
        number(Component.literal("Signal (1-15)"), signal, Component.empty(), v -> signal = v);
    }

    @Override
    protected DataResult<TrapControllerBlock.Config> settings() {
        return cost(signal, "Signal").flatMap(strength -> strength < 1 || strength > 15
                ? DataResult.error(() -> "Signal must be 1 to 15")
                : DataResult.success(new TrapControllerBlock.Config(optionalCost(cost), requiresPower, optionalCost(activeTicks),
                        optionalCost(cooldownTicks), strength)));
    }
}
