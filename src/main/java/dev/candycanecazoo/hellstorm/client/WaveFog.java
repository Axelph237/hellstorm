package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Pulls the fog in during a special round with fog: at density 1 the far edge is a few blocks away. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class WaveFog {
    private static final float NEAREST = 6f;
    private static float density;

    private WaveFog() {}

    public static void set(float value) {
        density = Math.max(0, Math.min(1, value));
    }

    /** Where the fog ends at {@code density}: falling off quickly, so 0.6 already feels close. */
    static float farPlane(float normal, float density) {
        float clear = (1 - density) * (1 - density);
        return Math.max(NEAREST, normal * clear);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (density <= 0) {
            return;
        }
        float far = Math.min(event.getFarPlaneDistance(), farPlane(event.getFarPlaneDistance(), density));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        density = 0;
    }
}
