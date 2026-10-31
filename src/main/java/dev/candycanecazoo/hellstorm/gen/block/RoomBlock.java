package dev.candycanecazoo.hellstorm.gen.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.feature.ConfigBlockEntity;
import dev.candycanecazoo.hellstorm.feature.MachineSettings;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.registry.GenRegistries;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A zombies-map structure block: it saves the box of blocks at {@code offset} from it, {@code size} big, as a room into
 * the pack {@code pack} in {@code hellstorm/maps/}, under {@code room}, with its tags, weight and max. Use it as an op in
 * creative to set it; its screen's Save room button saves. Its box is outlined while it's in view.
 */
public final class RoomBlock extends BaseEntityBlock {
    public static final int MAX_SIZE = 128;
    public static final Pattern TAG = Pattern.compile("[a-z0-9_]{1,32}");

    public record Config(String pack, String room, List<String> tags, int weight, Optional<Integer> max, BlockPos offset, Vec3i size) {
        public static final Config DEFAULT = new Config("workshop", "room", List.of(), 1, Optional.empty(), new BlockPos(1, 0, 1), new Vec3i(9, 6, 9));
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.STRING, "pack", DEFAULT.pack()).forGetter(Config::pack),
                Codecs.strictOptional(Codec.STRING, "room", DEFAULT.room()).forGetter(Config::room),
                Codecs.strictOptional(Codec.STRING.listOf(), "tags", List.of()).forGetter(Config::tags),
                Codecs.strictOptional(Codec.INT, "weight", 1).forGetter(Config::weight),
                Codecs.strictOptional(Codec.INT, "max").forGetter(Config::max),
                Codecs.strictOptional(BlockPos.CODEC, "offset", DEFAULT.offset()).forGetter(Config::offset),
                Codecs.strictOptional(Vec3i.CODEC, "size", DEFAULT.size()).forGetter(Config::size)
        ).apply(i, Config::new));

        /** What's wrong with these settings, if anything. */
        public Optional<String> problem() {
            if (!MapPack.NAME.matcher(pack).matches()) {
                return Optional.of("A pack name is up to 32 lowercase letters, digits, - and _");
            }
            if (!RoomPack.ROOM_ID.matcher(room).matches()) {
                return Optional.of("A room name is up to 64 lowercase letters, digits, - and _");
            }
            if (tags.stream().anyMatch(t -> !TAG.matcher(t).matches())) {
                return Optional.of("A tag is up to 32 lowercase letters, digits and _");
            }
            if (weight < 0 || max.map(m -> m < 1).orElse(false)) {
                return Optional.of("Weight can't be below 0, and max is at least 1");
            }
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getX() > MAX_SIZE || size.getY() > MAX_SIZE || size.getZ() > MAX_SIZE) {
                return Optional.of("A room is 1 to " + MAX_SIZE + " blocks each way");
            }
            return Optional.empty();
        }
    }

    public static final class Entity extends ConfigBlockEntity<Config> {
        public Entity(BlockPos pos, BlockState state) {
            super(GenRegistries.ROOM_BLOCK_ENTITY.get(), pos, state, Config.CODEC, Config.DEFAULT);
        }

        @Override
        protected Optional<String> problem(Config config) {
            return config.problem();
        }

        /** The room's corners, both in. */
        public BlockPos min() {
            return worldPosition.offset(config().offset());
        }

        public BlockPos max() {
            Vec3i size = config().size();
            return min().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(min(), max().offset(1, 1, 1)).minmax(new AABB(worldPosition));
        }
    }

    public RoomBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            MachineSettings.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
