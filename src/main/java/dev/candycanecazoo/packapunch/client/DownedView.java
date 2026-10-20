package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.Downs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How being down looks on this client: downed players crawl, and while you're down the screen goes black and white.
 * The server forces the crawl too, but a forced pose isn't synced and each client recomputes every player's pose, so each
 * client applies the crawl after its tick to the players the HUD says are down (see {@link Downs#crawl}).
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT)
public final class DownedView {
    private static final ResourceLocation SHADER = ResourceLocation.tryBuild(PackAPunch.MOD_ID, "shaders/post/downed.json");

    private DownedView() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null) {
            return;
        }
        for (Player player : mc.level.players()) {
            boolean down = player == mc.player ? HudState.selfDowned() : HudState.downed(player);
            if (down) {
                Downs.crawl(player);
            } else if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
        }
        PostChain current = mc.gameRenderer.currentEffect();
        boolean showing = current != null && current.getName().equals(SHADER.toString());
        if (HudState.selfDowned() && !showing) {
            mc.gameRenderer.loadEffect(SHADER);
        } else if (!HudState.selfDowned() && showing) {
            mc.gameRenderer.shutdownEffect();
        }
    }
}
