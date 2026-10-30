package dev.candycanecazoo.hellstorm.tacz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import dev.candycanecazoo.hellstorm.tacz.GunModelCapture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/** While a gun is being measured ({@link GunModelCapture}), its parts are drawn into the measuring consumer, not Minecraft's shared buffers. */
@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockModelMixin {
    @Shadow
    @Final
    protected List<BedrockPart> shouldRender;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void hellstorm$capture(PoseStack pose, ItemDisplayContext context, RenderType type, int light, int overlay,
                                   float red, float green, float blue, float alpha, CallbackInfo ci) {
        Optional<VertexConsumer> target = GunModelCapture.target();
        if (target.isEmpty()) {
            return;
        }
        pose.pushPose();
        for (BedrockPart part : shouldRender) {
            part.render(pose, context, target.get(), light, overlay, red, green, blue, alpha);
        }
        pose.popPose();
        ci.cancel();
    }
}
