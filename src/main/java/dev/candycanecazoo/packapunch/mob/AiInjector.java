package dev.candycanecazoo.packapunch.mob;

import dev.candycanecazoo.packapunch.mob.goals.GameMeleeGoal;
import dev.candycanecazoo.packapunch.mob.goals.TargetGamePlayerGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.function.Supplier;

/** Turns any mob into a game zombie: goals, round-scaled attributes, and persistence. */
public final class AiInjector {
    public record Stats(double health, double speed, float damage) {}

    private AiInjector() {}

    /**
     * Applies the profile to the mob. Brain-driven mobs always get {@code augment}, whatever the profile says, because
     * clearing their goals leaves the brain in charge anyway. Returns the AI mode actually used.
     */
    public static MobProfile.Ai inject(Mob mob, MobProfile profile, Stats stats, Supplier<? extends Collection<? extends Player>> targets) {
        SupportTier tier = SupportTier.of(mob);
        MobProfile.Ai mode = tier == SupportTier.PARTIAL_BRAIN ? MobProfile.Ai.AUGMENT : profile.ai();
        if (mode == MobProfile.Ai.REPLACE) {
            mob.goalSelector.removeAllGoals(goal -> true);
            mob.targetSelector.removeAllGoals(goal -> true);
            mob.goalSelector.addGoal(0, new FloatGoal(mob));
        }
        mob.targetSelector.addGoal(0, new TargetGamePlayerGoal(mob, targets));
        if (mob instanceof PathfinderMob walker) {
            mob.goalSelector.addGoal(1, new GameMeleeGoal(walker, stats.damage()));
        }
        setBase(mob, Attributes.MAX_HEALTH, stats.health());
        mob.setHealth((float) stats.health());
        setBase(mob, Attributes.MOVEMENT_SPEED, stats.speed());
        setBase(mob, Attributes.FOLLOW_RANGE, 64.0);
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);
        return mode;
    }

    private static void setBase(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
