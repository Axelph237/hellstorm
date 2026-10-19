package dev.candycanecazoo.packapunch.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.CombatLog;
import dev.candycanecazoo.packapunch.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Turns TacZ's hit events into game calls. TacZ posts each event on both sides, so only the server copy is used. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class TaczEvents {
    private TaczEvents() {}

    /**
     * Keeps an unlimited gun's reserve full every tick it's held. Topping up on reload alone isn't enough: TacZ refuses a reload
     * before posting its reload event once the reserve is empty, and one reload can use the whole reserve.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            TaczBridge.topUpUnlimitedReserve(event.getEntity().getMainHandItem());
        }
    }

    @SubscribeEvent
    public static void onGunHit(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && event.getHurtEntity() != null
                && event.getAttacker() instanceof ServerPlayer player
                && GameManager.forMob(event.getHurtEntity().getUUID()).isPresent()) {
            CombatLog.recordGunHit(event.getHurtEntity().getUUID(), player.getUUID(), event.isHeadShot(), player.level().getGameTime());
        }
    }

    /** Post fires only when the target survived, so it is exactly a non-lethal hit. */
    @SubscribeEvent
    public static void onGunHitSurvived(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && event.getHurtEntity() != null && event.getAttacker() instanceof ServerPlayer player) {
            GameManager.forMob(event.getHurtEntity().getUUID())
                    .ifPresent(a -> a.game().onMobHit(event.getHurtEntity().getUUID(), player.getUUID()));
        }
    }
}
