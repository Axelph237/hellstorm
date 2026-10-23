package dev.candycanecazoo.hellstorm.mob;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Shooting a zombie's arm or leg takes it off, once that limb has taken {@link #LIMB_SHARE} of the zombie's health in
 * shots: two pistol shots early on, more in later rounds. Losing a leg, or both, makes it a crawler at
 * {@link #CRAWL_SPEED} of its speed. Only standing {@code minecraft:zombie}s lose limbs; a crawler keeps what it has left.
 */
public final class Dismemberment {
    public static final float LIMB_SHARE = 0.4f;
    public static final float CRAWL_SPEED = 0.5f;
    private static final String DAMAGE = Limbs.REMOVED + "_damage";

    private Dismemberment() {}

    /** A bullet struck {@code mob} at {@code hit} for {@code damage}, and it lived. Returns the limb it took off, if any. */
    public static Optional<BodyPart> shot(Mob mob, Vec3 hit, boolean headshot, float damage) {
        if (mob.getType() != EntityType.ZOMBIE || Limbs.crawls(mob)) {
            return Optional.empty();
        }
        BodyPart part = BodyPart.at(hit, mob.position(), mob.getBbWidth(), mob.getBbHeight(), mob.yBodyRot, headshot);
        if (part.bit() == 0 || Limbs.lost(mob, part)) {
            return Optional.empty();
        }
        CompoundTag taken = mob.getPersistentData().getCompound(DAMAGE);
        float total = taken.getFloat(part.name()) + damage;
        if (total < LIMB_SHARE * mob.getMaxHealth()) {
            taken.putFloat(part.name(), total);
            mob.getPersistentData().put(DAMAGE, taken);
            return Optional.empty();
        }
        Limbs.remove(mob, part);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)), hit.x, hit.y, hit.z, 12, 0.1, 0.1, 0.1, 0.08);
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8f, 0.7f);
        }
        if (part.leg()) {
            Limbs.crawl(mob, CRAWL_SPEED);
        }
        return Optional.of(part);
    }
}
