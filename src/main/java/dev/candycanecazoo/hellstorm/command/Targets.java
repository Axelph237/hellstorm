package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** The machine a command applies to: the one the player is looking at, whichever of its blocks. */
public final class Targets {
    private static final double REACH = 8;

    private Targets() {}

    /** Fails the command with the machine's reason when its new settings were refused. */
    static void require(Optional<String> problem) throws CommandSyntaxException {
        if (problem.isPresent()) {
            throw CommandErrors.failAll(List.of(problem.get()), List.of());
        }
    }

    public static <T extends BlockEntity> T lookedAt(ServerPlayer player, Class<T> type, String what) throws CommandSyntaxException {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            if (state.getBlock() instanceof FeatureBlock machine) {
                pos = machine.mainPos(state, pos);
            }
            if (type.isInstance(player.level().getBlockEntity(pos))) {
                return type.cast(player.level().getBlockEntity(pos));
            }
        }
        throw CommandErrors.fail("Look at a {} (within {} blocks)", what, (int) REACH);
    }
}
