package dev.candycanecazoo.packapunch.tacz.mixin;

import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import dev.candycanecazoo.packapunch.tacz.ReloadTimes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Advances reload animations, and the sounds keyed to them, at the local player's Speed Cola pace. Both ways a runner
 * moves forward (drawn, and sound only) go through {@code updateProgress}. See {@link ReloadTimes}.
 */
@Mixin(value = ObjectAnimationRunner.class, remap = false)
public abstract class ObjectAnimationRunnerMixin {
    @Shadow
    @Final
    private ObjectAnimation animation;

    @ModifyArg(method = {"update", "updateSoundOnly"}, require = 2, at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/client/animation/ObjectAnimationRunner;updateProgress(J)V"))
    private long packapunch$speedCola(long nanos) {
        return ReloadTimes.animationStep(animation.name, nanos);
    }
}
