package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import dev.candycanecazoo.hellstorm.tacz.GunModelCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a gun is being measured ({@link GunModelCapture}), only its own body is drawn: attachments, laser beams and the
 * accelerated path all draw into Minecraft's shared buffers.
 */
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class BedrockGunModelMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true)
    private void hellstorm$captureBody(PoseStack pose, ItemStack stack, ItemDisplayContext context, RenderType type, int light, int overlay, CallbackInfo ci) {
        if (GunModelCapture.target().isPresent()) {
            ((BedrockModel) (Object) this).render(pose, context, type, light, overlay);
            ci.cancel();
        }
    }
}
