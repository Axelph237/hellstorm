package dev.candycanecazoo.hellstorm.pap;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PunchResolverTest {
    private static final ResourceLocation SWORD_ID = ResourceLocation.tryBuild("mymap", "sword_mk2");
    private static final PunchDef SWORD = new PunchDef(new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "diamond_sword"), 1, Optional.empty()),
            Optional.empty(), Optional.empty(), PunchDef.CarryOver.NONE);
    private static final PunchDef PRICED = new PunchDef(SWORD.result(), Optional.empty(), Optional.of(1234), PunchDef.CarryOver.NONE);
    private static final Map<ResourceLocation, PunchDef> DEFS = Map.of(SWORD_ID, SWORD, ResourceLocation.tryBuild("mymap", "priced"), PRICED);

    static CompoundTag pap(boolean punchable, String punchId, int tier) {
        CompoundTag pap = new CompoundTag();
        pap.putBoolean(ItemSpec.PUNCHABLE, punchable);
        if (punchId != null) {
            pap.putString(ItemSpec.PUNCH_ID, punchId);
        }
        if (tier > 0) {
            pap.putInt(PunchResolver.TIER, tier);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(ItemSpec.PAP, pap);
        tag.putString("other", "kept");
        return tag;
    }

    private static PunchOutcome resolve(CompoundTag tag, boolean gun) {
        return PunchResolver.resolve(tag, gun, DEFS, 5000, 1);
    }

    /** A rejection leaves the tag as it was and costs nothing. */
    private static void assertRejected(PunchOutcome.RejectReason reason, CompoundTag tag, boolean gun) {
        CompoundTag before = tag == null ? null : tag.copy();
        PunchOutcome outcome = resolve(tag, gun);
        assertEquals(new PunchOutcome.Rejected(reason), outcome);
        assertEquals(0, outcome.cost());
        assertEquals(before, tag);
    }

    @Test
    void itemsWithoutPunchableTrueAreNotPunchable() {
        assertRejected(PunchOutcome.RejectReason.NOT_PUNCHABLE, null, true);
        assertRejected(PunchOutcome.RejectReason.NOT_PUNCHABLE, new CompoundTag(), false);
        assertRejected(PunchOutcome.RejectReason.NOT_PUNCHABLE, pap(false, "mymap:sword_mk2", 0), false);
    }

    @Test
    void aKnownPunchIdIsReplacedAtTheDefsCostOrTheDefault() {
        assertEquals(new PunchOutcome.Replace(SWORD_ID, SWORD, 5000), resolve(pap(true, "mymap:sword_mk2", 0), false));
        assertEquals(new PunchOutcome.Replace(ResourceLocation.tryBuild("mymap", "priced"), PRICED, 1234), resolve(pap(true, "mymap:priced", 0), true));
    }

    @Test
    void aPunchIdWinsOverTheDefaultGunUpgradeAndTheTierCap() {
        assertInstanceOf(PunchOutcome.Replace.class, resolve(pap(true, "mymap:sword_mk2", 5), true));
    }

    @Test
    void anUnknownOrMalformedPunchIdIsRejected() {
        assertRejected(PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID, pap(true, "mymap:missing", 0), true);
        assertRejected(PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID, pap(true, "Not An Id!", 0), false);
        CompoundTag numeric = pap(true, null, 0);
        numeric.getCompound(ItemSpec.PAP).putInt(ItemSpec.PUNCH_ID, 3);
        assertRejected(PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID, numeric, false);
    }

    @Test
    void aTaczGunWithNoPunchIdGetsTheDefaultUpgrade() {
        assertEquals(new PunchOutcome.UpgradeTacz(5000), resolve(pap(true, null, 0), true));
    }

    @Test
    void anythingElseWithNoPunchIdHasNoUpgradePath() {
        assertRejected(PunchOutcome.RejectReason.NO_UPGRADE_PATH, pap(true, null, 0), false);
    }

    @Test
    void aGunAtTheMaxTierIsRejected() {
        assertRejected(PunchOutcome.RejectReason.MAX_TIER, pap(true, null, 1), true);
        assertEquals(new PunchOutcome.UpgradeTacz(5000), PunchResolver.resolve(pap(true, null, 1), true, DEFS, 5000, 2));
    }
}
