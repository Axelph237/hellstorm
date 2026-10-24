package dev.candycanecazoo.hellstorm.feature;

import java.util.List;

/** When a vending machine's key clicks sound: as each of the use animation's three keys hits bottom. */
public final class VendTiming {
    /** A click {@code tick} ticks into the vend, at {@code pitch}. */
    public record Press(int tick, float pitch) {}

    static final List<Press> PRESSES = List.of(new Press(3, 1.0f), new Press(6, 1.12f), new Press(9, 0.94f));

    private VendTiming() {}

    /**
     * The clicks to play on reaching {@code tick} of a vend, having played up to {@code heard}: those after one and up to the
     * other. None when {@code tick} hasn't passed {@code heard}, so no click plays twice.
     */
    static List<Press> pressesDue(long heard, long tick) {
        return PRESSES.stream().filter(press -> heard < press.tick() && press.tick() <= tick).toList();
    }
}
