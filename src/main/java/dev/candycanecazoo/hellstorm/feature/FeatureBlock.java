package dev.candycanecazoo.hellstorm.feature;

import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A machine players buy from: wall-buy, perk machine, Mystery Crate, the upgrader, power switch. The map indexes where
 * each one is, so a game knows about machines in unloaded chunks. Players in a game buy by right clicking or with the
 * Hellstorm use key. Outside a game, ops in creative mode get the machine's settings screen and anyone else gets its
 * settings in chat.
 */
public interface FeatureBlock {
    FeatureKind featureKind();

    /** A player in a game in this level used the machine. */
    void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state);

    /** One line for map makers: what the machine is set to. */
    Component describe(ServerLevel level, BlockPos pos);

    /** The block that holds a machine's settings and that the map indexes, for machines more than one block big. */
    default BlockPos mainPos(BlockState state, BlockPos pos) {
        return pos;
    }

    /** Every block the machine covers, given any one of them. */
    default List<BlockPos> blocks(BlockState state, BlockPos pos) {
        return List.of(pos);
    }

    static void use(ServerPlayer player, BlockPos clicked) {
        ServerLevel level = player.serverLevel();
        BlockState clickedState = level.getBlockState(clicked);
        if (!(clickedState.getBlock() instanceof FeatureBlock clickedBlock)) {
            return;
        }
        BlockPos pos = clickedBlock.mainPos(clickedState, clicked);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FeatureBlock block)) {
            return;
        }
        GameManager.forPlayer(player.getUUID()).filter(a -> a.level() == level).ifPresentOrElse(
                game -> block.buy(player, game, pos, state),
                () -> {
                    if (!MachineSettings.open(player, pos)) {
                        player.sendSystemMessage(block.describe(level, pos));
                    }
                });
    }

    /** Call from {@code onPlace}. */
    static void placed(Level level, BlockPos pos, BlockState state, BlockState oldState, FeatureKind kind) {
        if (level instanceof ServerLevel serverLevel && !oldState.is(state.getBlock())) {
            MapEdits.apply(serverLevel, map -> map.withFeature(pos, kind));
        }
    }

    /** Call from {@code onRemove}. */
    static void removed(Level level, BlockPos pos, BlockState state, BlockState newState) {
        if (level instanceof ServerLevel serverLevel && !newState.is(state.getBlock())) {
            MapEdits.apply(serverLevel, map -> map.withoutFeature(pos));
        }
    }
}
