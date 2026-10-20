package dev.candycanecazoo.packapunch.feature;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Where a trap is in its cycle: ready to buy, giving its redstone signal, or cooling down. {@code until} is the game time
 * the current phase ends (unused while ready). Pure, so the timing can be tested without a world.
 */
public record TrapCycle(Phase phase, long until) {
    public enum Phase implements StringRepresentable {
        READY, ACTIVE, COOLDOWN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final TrapCycle READY = new TrapCycle(Phase.READY, 0);

    /** Bought at {@code now}: the signal is on for {@code activeTicks} game ticks. */
    public static TrapCycle activated(long now, int activeTicks) {
        return new TrapCycle(Phase.ACTIVE, now + activeTicks);
    }

    /** The cycle at {@code now}: active turns to cooldown when its time is up, and cooldown to ready. */
    public TrapCycle at(long now, int cooldownTicks) {
        if (phase == Phase.ACTIVE && now >= until) {
            return cooldownTicks > 0 ? new TrapCycle(Phase.COOLDOWN, until + cooldownTicks) : READY;
        }
        if (phase == Phase.COOLDOWN && now >= until) {
            return READY;
        }
        return this;
    }

    public boolean powered() {
        return phase == Phase.ACTIVE;
    }

    /** Whole seconds until this phase ends, rounded up. */
    public int secondsLeft(long now) {
        return (int) Math.max(0, (until - now + 19) / 20);
    }
}
