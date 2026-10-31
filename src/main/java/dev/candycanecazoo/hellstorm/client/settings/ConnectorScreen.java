package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.gen.block.ConnectorBlock;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A connector's settings: what its opening becomes when another room joins it, and its group. */
final class ConnectorScreen extends SettingsScreen<ConnectorBlock.Config> {
    private Connector.Kind kind;
    private String group;

    ConnectorScreen(ConnectorBlock.Entity entity) {
        super(Component.literal("Room Connector"), entity);
        kind = entity.config().kind();
        group = entity.config().group();
    }

    @Override
    protected void rows() {
        choice(Component.literal("Joined, it becomes"), List.of(Connector.Kind.values()), kind,
                k -> Component.literal(k == Connector.Kind.DOOR ? "A door to buy" : "Open"), k -> kind = k);
        text(Component.literal("Group"), group, 32, Component.literal("blank fits any opening its size"), v -> group = v);
    }

    @Override
    protected DataResult<ConnectorBlock.Config> settings() {
        String g = group.trim();
        return ConnectorBlock.GROUP.matcher(g).matches() ? DataResult.success(new ConnectorBlock.Config(kind, g))
                : DataResult.error(() -> "A group is up to 32 lowercase letters, digits and underscores");
    }
}
