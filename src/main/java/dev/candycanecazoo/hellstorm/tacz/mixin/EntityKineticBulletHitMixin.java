package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.candycanecazoo.hellstorm.tacz.BulletHits;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes where a bullet struck an entity, which TacZ's hurt events don't carry: they're posted while it handles the hit,
 * so {@link BulletHits} hands the point to them. Only TacZ's own method is named, so it needs no refmap.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletHitMixin {
    @Inject(method = "onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void hellstorm$noteHit(TacHitResult result, Vec3 start, Vec3 end, CallbackInfo info) {
        BulletHits.note((EntityKineticBullet) (Object) this, result.getLocation());
    }
}
