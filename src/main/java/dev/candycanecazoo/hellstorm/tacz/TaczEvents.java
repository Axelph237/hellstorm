package dev.candycanecazoo.hellstorm.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.data.GunBalances;
import dev.candycanecazoo.hellstorm.game.CombatLog;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.mob.Dismemberment;
import net.minecraft.world.entity.Mob;
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

    /** The gun's {@link GunBalances balance} scales every hit. Only the server's copy matters: it's the one that hurts. */
    @SubscribeEvent
    public static void onGunDamage(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && event.getGunId() != null) {
            double multiplier = GunBalances.of(event.getGunId()).damageMultiplier();
            if (multiplier != 1.0) {
                event.setBaseAmount((float) (event.getBaseAmount() * multiplier));
            }
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
            GameManager.forMob(event.getHurtEntity().getUUID()).ifPresent(a -> {
                a.game().onMobHit(event.getHurtEntity().getUUID(), player.getUUID());
                if (event.getHurtEntity() instanceof Mob mob) {
                    BulletHits.of(event.getBullet()).ifPresent(hit -> Dismemberment.shot(mob, hit, event.isHeadShot(), event.getAmount()));
                }
            });
        }
    }
}
