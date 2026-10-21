package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.candycanecazoo.hellstorm.tacz.ReloadTimes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every reload, scripted or not, measures its progress here. See {@link ReloadTimes}. */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class ModernKineticGunScriptAPIMixin {
    @Shadow
    private LivingEntity shooter;

    @Inject(method = "getReloadTime", at = @At("RETURN"), cancellable = true)
    private void hellstorm$reLoad(CallbackInfoReturnable<Long> cir) {
        if (shooter != null) {
            cir.setReturnValue(ReloadTimes.elapsed(shooter, cir.getReturnValue()));
        }
    }
}
