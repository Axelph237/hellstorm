package dev.candycanecazoo.packapunch.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.perk.PerkEffect;
import dev.candycanecazoo.packapunch.perk.Perks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;

/**
 * Perk effects on TacZ guns. Each multiplies what is already there, since Pack-a-Punch changes the same values in the same
 * events and the two must stack.
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class PerkGunEffects {
    private PerkGunEffects() {}

    @SubscribeEvent
    public static void onGunHit(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && event.getAttacker() instanceof ServerPlayer player) {
            double multiplier = Perks.product(Perks.held(player), PerkEffect.DamageMultiplier.class, PerkEffect.DamageMultiplier::multiplier);
            if (multiplier != 1) {
                event.setBaseAmount((float) (event.getBaseAmount() * multiplier));
            }
        }
    }

    @SubscribeEvent
    public static void onProperties(AttachmentPropertyEvent event) {
        double multiplier = rpmMultiplier(event.getGunItem());
        Integer rpm = event.getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE);
        if (multiplier != 1 && rpm != null) {
            event.getCacheProperty().setCache(GunProperties.ROUNDS_PER_MINUTE, Math.max(1, (int) Math.round(rpm * multiplier)));
        }
    }

    /** Re-evaluates the player's held gun so a perk bought or lost changes it now, not on the next draw. */
    public static void refresh(ServerPlayer player) {
        AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
    }

    /**
     * The event only carries the stack. TacZ evaluates a gun for whoever holds it in their main hand, so the holder is the
     * game player (server thread) or the local player (client thread) whose main hand is this exact stack.
     */
    private static double rpmMultiplier(ItemStack gun) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            for (GameManager.Active active : GameManager.all()) {
                for (PlayerState state : active.game().players()) {
                    Optional<ServerPlayer> holder = active.world().entity(state.id());
                    if (holder.isPresent() && holder.get().getMainHandItem() == gun) {
                        return Perks.product(state.perks().values(), PerkEffect.RpmMultiplier.class, PerkEffect.RpmMultiplier::multiplier);
                    }
                }
            }
            return 1;
        }
        Double local = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> LocalGunPerks.rpmMultiplier(gun));
        return local == null ? 1 : local;
    }
}
