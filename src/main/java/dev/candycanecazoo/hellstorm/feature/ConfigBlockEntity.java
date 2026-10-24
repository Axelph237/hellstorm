package dev.candycanecazoo.hellstorm.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

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

    public Codec<C> codec() {
        return codec;
    }

    /** Throws if the codec can't save {@code config}: it would be dropped at the next save. Use {@link #trySet} for input. */
    public void setConfig(C config) {
        unsaveable(config).ifPresent(error -> {
            throw new IllegalArgumentException("Settings for " + getType() + " at " + worldPosition + " can't be saved: " + error);
        });
        this.config = config;
        changed();
    }

    /** Why these settings can't be used here: the codec can't save them, or {@link #problem} refuses them. */
    public Optional<String> check(C config) {
        Optional<String> unsaveable = unsaveable(config);
        return unsaveable.isPresent() ? unsaveable : problem(config);
    }

    private Optional<String> unsaveable(C config) {
        return codec.encodeStart(NbtOps.INSTANCE, config).error().map(DataResult.PartialResult::message);
    }

    /** Sets the settings if they make sense here (they save, the perk is loaded, the gun exists). Returns why not otherwise. */
    public Optional<String> trySet(C config) {
        Optional<String> problem = check(config);
        if (problem.isEmpty()) {
            setConfig(config);
        }
        return problem;
    }

    /** Settings sent from the settings screen, parsed with this block's codec, then {@link #trySet}. */
    public Optional<String> trySet(Tag tag) {
        DataResult<C> parsed = codec.parse(NbtOps.INSTANCE, tag);
        return parsed.result().map(this::trySet).orElseGet(() -> Optional.of(parsed.error().map(e -> e.message()).orElse("Invalid settings")));
    }

    /** Why these settings can't be used, beyond what the codec checks. */
    protected Optional<String> problem(C config) {
        return Optional.empty();
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
                    .resultOrPartial(error -> Hellstorm.LOG.error("Bad config on {} at {}: {}", getType(), worldPosition, error))
                    .ifPresent(c -> config = c);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        DataResult<Tag> encoded = codec.encodeStart(NbtOps.INSTANCE, config);
        encoded.error().ifPresent(error -> Hellstorm.LOG.error("Couldn't save the settings of {} at {}: {}", getType(), worldPosition, error.message()));
        encoded.result().ifPresent(t -> tag.put(CONFIG, t));
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
