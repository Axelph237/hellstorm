package dev.candycanecazoo.packapunch.perk;

import dev.candycanecazoo.packapunch.game.Refusal;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PerksTest {
    private static final ResourceLocation JUG = ResourceLocation.tryBuild("packapunch", "juggernog");
    private static final ResourceLocation TAP = ResourceLocation.tryBuild("packapunch", "double_tap");
    private static final ResourceLocation COLA = ResourceLocation.tryBuild("packapunch", "speed_cola");

    @Test
    void aPerkCanBeBoughtOnceAndOnlyUpToTheLimit() {
        assertEquals(Optional.empty(), Perks.refusal(Set.of(), JUG, "Juggernog", 4));
        assertEquals(Optional.of(new Refusal.Because("You already have Juggernog")), Perks.refusal(Set.of(JUG), JUG, "Juggernog", 4));
        assertEquals(Optional.empty(), Perks.refusal(Set.of(JUG), TAP, "Double Tap", 2));
        assertEquals(Optional.of(new Refusal.Because("You can only hold 2 perks")), Perks.refusal(Set.of(JUG, TAP), COLA, "Speed Cola", 2));
        assertEquals(Optional.of(new Refusal.Because("You can only hold 1 perk")), Perks.refusal(Set.of(JUG), COLA, "Speed Cola", 1));
        assertTrue(Perks.refusal(Set.of(), JUG, "Juggernog", 0).isPresent(), "a limit of 0 sells nothing");
    }

    @Test
    void multipliersOfOneKindMultiplyAcrossPerks() {
        PerkDef tap = new PerkDef("perk.tap", 2000, true, 0, List.of(new PerkEffect.RpmMultiplier(1.5), new PerkEffect.DamageMultiplier(2)));
        PerkDef more = new PerkDef("perk.more", 2000, true, 0, List.of(new PerkEffect.RpmMultiplier(2)));
        PerkDef jug = new PerkDef("perk.jug", 2500, true, 0, List.of(new PerkEffect.WeaponSlots(1)));
        assertEquals(3.0, Perks.product(List.of(tap, more, jug), PerkEffect.RpmMultiplier.class, PerkEffect.RpmMultiplier::multiplier), 1e-9);
        assertEquals(2.0, Perks.product(List.of(tap, more, jug), PerkEffect.DamageMultiplier.class, PerkEffect.DamageMultiplier::multiplier), 1e-9);
        assertEquals(1.0, Perks.product(List.of(jug), PerkEffect.RpmMultiplier.class, PerkEffect.RpmMultiplier::multiplier), "no effect of that kind");
        assertEquals(1.0, Perks.product(List.of(), PerkEffect.RpmMultiplier.class, PerkEffect.RpmMultiplier::multiplier), "no perks");
    }
}
