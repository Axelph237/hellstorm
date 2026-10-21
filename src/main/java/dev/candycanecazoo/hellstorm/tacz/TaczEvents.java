package dev.candycanecazoo.hellstorm.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.util.block.ProjectileExplosion;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.CombatLog;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Turns TacZ's hit events into game calls. TacZ posts each event on both sides, so only the server copy is used. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
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

    /**
     * Guns never break blocks. TacZ's explosive ammo (rockets, grenade launchers) blows up terrain by default
     * ({@code ExplosiveAmmoDestroysBlock}) and can set it alight ({@code ExplosiveAmmoFire}); emptying the blast's block list
     * stops both, and the blast still hurts and knocks back. This holds outside games too: a map is built and tested with the
     * same guns, and one test shot shouldn't cost its builder a wall. The bullet's own block damage is in {@code mixin}.
     */
    @SubscribeEvent
    public static void onGunExplosion(ExplosionEvent.Detonate event) {
        if (event.getExplosion() instanceof ProjectileExplosion) {
            event.getAffectedBlocks().clear();
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
