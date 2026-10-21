package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import dev.candycanecazoo.hellstorm.game.GameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bullets break no glass while a match is underway ({@link GameManager#matchUnderway}). TacZ shatters glass, stained glass,
 * and panes from its own {@code AmmoHitBlockEvent} handler ({@code DestroyGlass}, on by default). Cancelling the event
 * instead would also skip the rest of the hit: the bullet would fly on through the block.
 */
@Mixin(value = DestroyGlassBlock.class, remap = false)
public abstract class DestroyGlassBlockMixin {
    @Inject(method = "onAmmoHitBlock", at = @At("HEAD"), cancellable = true)
    private static void hellstorm$keepGlass(AmmoHitBlockEvent event, CallbackInfo ci) {
        if (GameManager.matchUnderway(event.getLevel())) {
            ci.cancel();
        }
    }
}
