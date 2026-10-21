package dev.candycanecazoo.hellstorm.mob.goals;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Supplier;

/** Targets the nearest player the game offers (alive players in this game). Rechecks a few times a second. */
public final class TargetGamePlayerGoal extends Goal {
    private static final int RECHECK_TICKS = 10;

    private final Mob mob;
    private final Supplier<? extends Collection<? extends Player>> candidates;
    private int cooldown;

    public TargetGamePlayerGoal(Mob mob, Supplier<? extends Collection<? extends Player>> candidates) {
        this.mob = mob;
        this.candidates = candidates;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--cooldown > 0) {
            return;
        }
        cooldown = RECHECK_TICKS;
        Collection<? extends Player> players = candidates.get();
        if (mob.getTarget() instanceof Player current && !players.contains(current)) {
            mob.setTarget(null);
        }
        players.stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && p.level() == mob.level())
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .ifPresent(mob::setTarget);
    }
}
