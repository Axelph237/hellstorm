package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps each client's copy of its level's map current, drops per-player tool state on logout, and edit history on stop. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class ToolEvents {
    private ToolEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MapEdits.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ToolState.forget(player.getUUID());
            MapEdits.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MapEdits.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MapHistory.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ToolState.forget(event.getEntity().getUUID());
    }
}
