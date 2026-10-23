package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.util.block.ProjectileExplosion;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A gun's explosion pushes nothing while a match is underway ({@link GameManager#matchUnderway}), as in CoD, where
 * explosives hurt but don't throw. TacZ reads its {@code knockback} flag once per entity hit, guarding both the push and the
 * knockback it sends a hit player's client; here it reads false during a match. The damage is unchanged.
 */
@Mixin(value = ProjectileExplosion.class, remap = false)
public abstract class ProjectileExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private boolean knockback;

    @Redirect(method = "explode()V", at = @At(value = "FIELD", target = "Lcom/tacz/guns/util/block/ProjectileExplosion;knockback:Z", opcode = Opcodes.GETFIELD))
    private boolean hellstorm$noPushInAMatch(ProjectileExplosion explosion) {
        return knockback && !GameManager.matchUnderway(level);
    }
}
