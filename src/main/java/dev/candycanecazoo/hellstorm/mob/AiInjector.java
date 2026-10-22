package dev.candycanecazoo.hellstorm.mob;

import dev.candycanecazoo.hellstorm.mob.goals.GameMeleeGoal;
import dev.candycanecazoo.hellstorm.mob.goals.Navigator;
import dev.candycanecazoo.hellstorm.mob.goals.TargetGamePlayerGoal;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.function.Supplier;

/** Turns any mob into a game zombie: goals, round-scaled attributes, and persistence. */
public final class AiInjector {
    public record Stats(double health, double speed, float damage) {}

    /** Long enough for mobs on vanilla navigation to find players across a map. */
    private static final double FOLLOW_RANGE = 128.0;

    private AiInjector() {}

    /**
     * Applies the profile to the mob. Brain-driven mobs always get {@code augment}, whatever the profile says, because
     * clearing their goals leaves the brain in charge anyway. Returns the AI mode actually used.
     */
    public static MobProfile.Ai inject(Mob mob, MobProfile profile, Stats stats, Supplier<? extends Collection<? extends Player>> targets) {
        return inject(mob, profile, stats, targets, Navigator.vanilla());
    }

    public static MobProfile.Ai inject(Mob mob, MobProfile profile, Stats stats, Supplier<? extends Collection<? extends Player>> targets,
                                       Navigator navigator) {
        SupportTier tier = SupportTier.of(mob);
        MobProfile.Ai mode = tier == SupportTier.PARTIAL_BRAIN ? MobProfile.Ai.AUGMENT : profile.ai();
        if (mode == MobProfile.Ai.REPLACE) {
            mob.goalSelector.removeAllGoals(goal -> true);
            mob.targetSelector.removeAllGoals(goal -> true);
            mob.goalSelector.addGoal(0, new FloatGoal(mob));
        }
        mob.targetSelector.addGoal(0, new TargetGamePlayerGoal(mob, targets));
        if (mob instanceof PathfinderMob walker) {
            mob.goalSelector.addGoal(1, new GameMeleeGoal(walker, stats.damage(), navigator));
        }
        if (mob.getNavigation() instanceof GroundPathNavigation ground) {
            // Wooden doors count as passages between zones (LevelCellProbe), so zombies must be able to open them.
            ground.setCanOpenDoors(true);
            mob.goalSelector.addGoal(2, new OpenDoorGoal(mob, false));
        }
        setBase(mob, Attributes.MAX_HEALTH, stats.health());
        mob.setHealth((float) stats.health());
        setBase(mob, Attributes.MOVEMENT_SPEED, stats.speed());
        setBase(mob, Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
        searchAsFarAsFollowRange(mob);
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);
        return mode;
    }

    /**
     * Vanilla navigation stops searching after 16 nodes per block of follow range, a budget it sizes once, when the mob is
     * constructed, from its type's default range (560 nodes for a zombie, 256 for a wolf). Raising the attribute
     * afterwards doesn't reach it, so this scales the budget to {@link #FOLLOW_RANGE}.
     */
    @SuppressWarnings("unchecked")
    private static void searchAsFarAsFollowRange(Mob mob) {
        double builtRange = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) mob.getType()).getBaseValue(Attributes.FOLLOW_RANGE);
        int builtBudget = Math.max(1, Mth.floor(builtRange * 16));
        mob.getNavigation().setMaxVisitedNodesMultiplier((float) (Mth.floor(FOLLOW_RANGE * 16) / (double) builtBudget));
    }

    private static void setBase(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
