package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Incendiary bullets set no block alight. When one hits a block, TacZ places fire in front of it if its {@code IgniteBlock}
 * config is on (the default); that read is the only config read in the hit, and here it answers no. Setting the mob it hits
 * on fire is a separate config and stays. See {@code TaczEvents.onGunExplosion} for why this holds outside games too.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin {
    @Redirect(method = "onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeConfigSpec$BooleanValue;get()Ljava/lang/Object;"))
    private Object hellstorm$noBlockFire(ForgeConfigSpec.BooleanValue igniteBlock) {
        return Boolean.FALSE;
    }
}
