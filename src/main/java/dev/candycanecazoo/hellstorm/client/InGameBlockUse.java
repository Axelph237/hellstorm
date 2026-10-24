package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client's half of {@link GameHooks#onRightClickBlock}: in a game, it doesn't open a door or flip a lever ahead of
 * the server, which would only shut it again. The click still reaches the server, which decides.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class InGameBlockUse {
    private InGameBlockUse() {}

    @SubscribeEvent
    public static void denyBlockUseInGame(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() && HudState.inGame() && !GameHooks.usableInGame(event.getLevel().getBlockState(event.getPos()))) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}
