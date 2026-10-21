package dev.candycanecazoo.hellstorm.net;

import dev.candycanecazoo.hellstorm.client.settings.SettingsScreens;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Opens the settings screen for the machine at {@code pos}, with what only the server knows: the box pools to pick from,
 * the item in the map maker's hand, and the default cost a blank cost falls back to.
 */
public record OpenSettingsPacket(BlockPos pos, List<Choice> choices, Optional<Held> held, int defaultCost) {
    public record Choice(ResourceLocation id, Component name) {}

    /** The item in the map maker's hand when they opened the screen, as a wall-buy would sell it. */
    public record Held(ItemSpec item, Component name) {}

    void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(choices.size());
        for (Choice choice : choices) {
            buf.writeResourceLocation(choice.id());
            buf.writeComponent(choice.name());
        }
        buf.writeOptional(held, (b, h) -> {
            b.writeNbt((CompoundTag) ItemSpec.CODEC.encodeStart(NbtOps.INSTANCE, h.item()).getOrThrow(false, e -> {}));
            b.writeComponent(h.name());
        });
        buf.writeVarInt(defaultCost);
    }

    static OpenSettingsPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<Choice> choices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            choices.add(new Choice(buf.readResourceLocation(), buf.readComponent()));
        }
        Optional<Held> held = buf.readOptional(b -> new Held(ItemSpec.CODEC.parse(NbtOps.INSTANCE, b.readNbt()).getOrThrow(false, e -> {}), b.readComponent()));
        return new OpenSettingsPacket(pos, List.copyOf(choices), held, buf.readVarInt());
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettingsScreens.open(this));
    }
}
