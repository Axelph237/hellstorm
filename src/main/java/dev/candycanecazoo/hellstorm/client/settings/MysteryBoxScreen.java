package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class MysteryBoxScreen extends SettingsScreen<MysteryBoxBlock.Config> {
    private final List<ResourceLocation> pools;
    private final int defaultCost;
    private ResourceLocation pool;
    private String cost;

    MysteryBoxScreen(MysteryBoxBlock.Entity entity, OpenSettingsPacket packet) {
        super(Component.literal("Mystery Crate"), entity);
        pool = entity.config().pool();
        pools = Choices.ids(packet.choices(), pool);
        defaultCost = packet.defaultCost();
        cost = entity.config().cost().map(String::valueOf).orElse("");
    }

    @Override
    protected void rows() {
        choice(Component.literal("Pool"), pools, pool, id -> Component.literal(id.toString()), v -> pool = v);
        number(Component.literal("Cost"), cost, Component.literal("Default (" + defaultCost + ")"), v -> cost = v);
    }

    @Override
    protected DataResult<MysteryBoxBlock.Config> settings() {
        return DataResult.success(new MysteryBoxBlock.Config(pool, optionalCost(cost)));
    }
}
