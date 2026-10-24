package dev.candycanecazoo.hellstorm.mob.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementClassTest {
    @Test
    void hitboxesRoundUpToWholeBlocks() {
        assertEquals(new MovementClass(1, 2), MovementClass.ofHitbox(0.6f, 1.95f));
        assertEquals(new MovementClass(2, 3), MovementClass.ofHitbox(1.95f, 2.2f));
        assertEquals(new MovementClass(1, 1), MovementClass.ofHitbox(1.0f, 1.0f));
    }
}
