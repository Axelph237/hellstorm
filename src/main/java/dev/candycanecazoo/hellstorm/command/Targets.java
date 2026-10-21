package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** The machine a command applies to: the block the player is looking at. */
public final class Targets {
    private static final double REACH = 8;
    private static final DynamicCommandExceptionType NOT_LOOKING =
            new DynamicCommandExceptionType(what -> Component.literal("Look at a " + what + " (within " + (int) REACH + " blocks)"));

    private Targets() {}

    /** Fails the command with the machine's reason when its new settings were refused. */
    /** A command error carrying {@code message}, to throw. */
    static CommandSyntaxException fail(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    static void require(Optional<String> problem) throws CommandSyntaxException {
        if (problem.isPresent()) {
            throw new SimpleCommandExceptionType(Component.literal(problem.get())).create();
        }
    }

    public static <T extends BlockEntity> T lookedAt(ServerPlayer player, Class<T> type, String what) throws CommandSyntaxException {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (type.isInstance(player.level().getBlockEntity(pos))) {
                return type.cast(player.level().getBlockEntity(pos));
            }
        }
        throw NOT_LOOKING.create(what);
    }
}
