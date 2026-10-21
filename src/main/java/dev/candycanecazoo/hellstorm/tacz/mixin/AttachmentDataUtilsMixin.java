package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import dev.candycanecazoo.hellstorm.tacz.PapGunStats;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Magazine capacity isn't in TacZ's property cache: reloads, the HUD, and gun scripts all ask this method. Scaling its
 * answer makes an upgraded magazine bigger everywhere, on both sides.
 */
@Mixin(value = AttachmentDataUtils.class, remap = false)
public abstract class AttachmentDataUtilsMixin {
    @Inject(method = "getAmmoCountWithAttachment", at = @At("RETURN"), cancellable = true)
    private static void hellstorm$papMagazine(ItemStack gunItem, GunData gunData, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PapGunStats.magazineSize(gunItem, cir.getReturnValue()));
    }
}
