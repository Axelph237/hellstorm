package dev.candycanecazoo.packapunch.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;

/**
 * The {@code packapunch:fire_rate} attribute (Double Tap) on TacZ guns. It multiplies what is already there, since
 * Pack-a-Punch changes the same value in the same event and the two must stack.
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class PerkGunEffects {
    private PerkGunEffects() {}

    @SubscribeEvent
    public static void onProperties(AttachmentPropertyEvent event) {
        double multiplier = holder(event.getGunItem()).map(p -> p.getAttributeValue(PapRegistries.FIRE_RATE)).orElse(1.0);
        Integer rpm = event.getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE);
        if (multiplier != 1 && rpm != null) {
            event.getCacheProperty().setCache(GunProperties.ROUNDS_PER_MINUTE, Math.max(1, (int) Math.round(rpm * multiplier)));
        }
    }

    /** Re-evaluates the player's held gun so a fire rate change applies now, not on the next draw. */
    public static void refresh(ServerPlayer player) {
        AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
    }

    /**
     * The event only carries the stack. TacZ evaluates a gun for whoever holds it in their main hand, so the holder is the
     * player (server thread) or the local player (client thread) whose main hand is this exact stack. Game players are
     * searched too, since test players aren't in the server's player list.
     */
    private static Optional<Player> holder(ItemStack gun) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getMainHandItem() == gun) {
                    return Optional.of(player);
                }
            }
            for (GameManager.Active active : GameManager.all()) {
                for (PlayerState state : active.game().players()) {
                    Optional<ServerPlayer> holder = active.world().entity(state.id()).filter(p -> p.getMainHandItem() == gun);
                    if (holder.isPresent()) {
                        return Optional.of(holder.get());
                    }
                }
            }
            return Optional.empty();
        }
        return Optional.ofNullable(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> LocalGunPerks.holder(gun)));
    }
}
