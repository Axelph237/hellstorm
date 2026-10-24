package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.text.DecimalFormat;

/**
 * TacZ's ammo HUD shows a gun with unlimited ammo (the starting kit's) with ∞ for its reserve, as it does for a player in
 * creative, instead of the reserve Hellstorm keeps topping up. The reserve is the second number the HUD formats.
 */
@Mixin(value = GunHudOverlay.class, remap = false)
public abstract class GunHudOverlayMixin {
    @Redirect(method = "render", require = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Ljava/text/DecimalFormat;format(J)Ljava/lang/String;"))
    private String hellstorm$infiniteReserve(DecimalFormat format, long reserve) {
        LocalPlayer player = Minecraft.getInstance().player;
        return TaczBridge.showsInfiniteReserve(player == null ? null : player.getMainHandItem(), reserve) ? TaczBridge.INFINITE : format.format(reserve);
    }
}
