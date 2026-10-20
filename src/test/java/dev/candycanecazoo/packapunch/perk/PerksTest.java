package dev.candycanecazoo.packapunch.perk;

import dev.candycanecazoo.packapunch.game.Refusal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PerksTest {
    @Test
    void aNewPerkIsRefusedOnlyWhenItWouldGoPastTheLimit() {
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of(), List.of("jug"), 4));
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of("jug", "tap", "cola"), List.of("stamin"), 4), "the fourth fits");
        assertEquals(Optional.of(new Refusal.Because("You can only hold 2 perks")), Perks.limitRefusal(Set.of("jug", "tap"), List.of("cola"), 2));
        assertEquals(Optional.of(new Refusal.Because("You can only hold 1 perk")), Perks.limitRefusal(Set.of("jug"), List.of("cola"), 1));
        assertTrue(Perks.limitRefusal(Set.of(), List.of("jug"), 0).isPresent(), "a limit of 0 allows no perks");
    }

    @Test
    void perksAlreadyHeldAndNonPerksNeverCount() {
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of("jug", "tap"), List.of("jug"), 2), "drinking a perk already held");
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of("jug", "tap"), List.of(), 2), "a potion with no perks");
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of("jug", "tap", "cola"), List.of(), 2), "already over the limit");
    }

    @Test
    void aPotionOfSeveralPerksMustFitWhole() {
        assertTrue(Perks.limitRefusal(Set.of("jug"), List.of("tap", "cola"), 2).isPresent(), "two new perks with room for one");
        assertEquals(Optional.empty(), Perks.limitRefusal(Set.of("jug"), List.of("tap", "tap", "jug"), 2), "repeats count once");
    }
}
