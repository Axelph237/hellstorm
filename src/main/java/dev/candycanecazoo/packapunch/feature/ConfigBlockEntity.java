package dev.candycanecazoo.packapunch.feature;

import com.mojang.serialization.Codec;
import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine block's settings (what it sells, for how much), saved with the block and synced to clients for prompts and
 * rendering. Settings travel with the block when a map is copied as a structure.
 */
public abstract class ConfigBlockEntity<C> extends BlockEntity {
    private static final String CONFIG = "config";

    private final Codec<C> codec;
    private C config;

    protected ConfigBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Codec<C> codec, C defaults) {
        super(type, pos, state);
        this.codec = codec;
        this.config = defaults;
    }

    public C config() {
        return config;
    }

    public void setConfig(C config) {
        this.config = config;
        changed();
    }

    /** Saves and sends the block entity to clients. Subclasses call it when their own synced state changes too. */
    protected void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(CONFIG)) {
            codec.parse(NbtOps.INSTANCE, tag.get(CONFIG))
                    .resultOrPartial(error -> PackAPunch.LOG.error("Bad config on {} at {}: {}", getType(), worldPosition, error))
                    .ifPresent(c -> config = c);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        codec.encodeStart(NbtOps.INSTANCE, config).result().ifPresent(t -> tag.put(CONFIG, t));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
