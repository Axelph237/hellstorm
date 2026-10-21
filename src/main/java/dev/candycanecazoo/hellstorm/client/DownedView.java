package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.Downs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * How being down looks on this client: downed players crawl, and while you're down the screen goes black and white.
 * The server forces the crawl too, but a forced pose isn't synced and each client recomputes every player's pose, so each
 * client applies the crawl after its tick to the players the HUD says are down (see {@link Downs#crawl}).
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class DownedView {
    /** Players this client put in the crawl, to stand them up again once they aren't down. */
    private static final Set<UUID> CRAWLING = new HashSet<>();
    private static final ResourceLocation SHADER = ResourceLocation.tryBuild(Hellstorm.MOD_ID, "shaders/post/downed.json");

    private DownedView() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            CRAWLING.clear();
            return;
        }
        for (Player player : mc.level.players()) {
            boolean down = player == mc.player ? HudState.selfDowned() : HudState.downed(player);
            if (down) {
                Downs.crawl(player);
                CRAWLING.add(player.getUUID());
            } else if (CRAWLING.remove(player.getUUID())) {
                // Stood up explicitly: a client never works out another player's pose again on its own, so they'd stay crawling.
                player.setForcedPose(null);
                player.setPose(Pose.STANDING);
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
