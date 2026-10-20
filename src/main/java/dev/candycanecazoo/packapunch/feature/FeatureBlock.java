package dev.candycanecazoo.packapunch.feature;

import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.tools.MapEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine players buy from: wall-buy, perk machine, Mystery Box, Pack-a-Punch, power switch. The map indexes where
 * each one is, so a game knows about machines in unloaded chunks. Players in a game buy by right clicking or with the
 * Pack-a-Punch use key. Outside a game, ops in creative mode get the machine's settings screen and anyone else gets its
 * settings in chat.
 */
public interface FeatureBlock {
    FeatureKind featureKind();

    /** A player in a game in this level used the machine. */
    void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state);

    /** One line for map makers: what the machine is set to. */
    Component describe(ServerLevel level, BlockPos pos);

    static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
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
