package dev.candycanecazoo.packapunch.pap;

import dev.candycanecazoo.packapunch.data.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PunchAuditTest {
    private static final ResourceLocation KNOWN = ResourceLocation.tryBuild("mymap", "known");
    private static final Map<ResourceLocation, PunchDef> DEFS = Map.of(KNOWN, new PunchDef(
            new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "diamond_sword"), 1, Optional.empty()), Optional.empty(), Optional.empty(),
            PunchDef.CarryOver.NONE));

    private static ItemSpec plain(String item, CompoundTag nbt) {
        return new ItemSpec.Plain(ResourceLocation.tryParse(item), 1, Optional.ofNullable(nbt));
    }

    private static PunchAudit.Entry entry(String source, ItemSpec spec) {
        return new PunchAudit.Entry(source, Optional.of(new BlockPos(1, 2, 3)), spec);
    }

    /** Plain items here are never TacZ guns, as the live check would find for a stick. */
    private static List<PunchAudit.Finding> check(PunchAudit.Entry... entries) {
        return PunchAudit.check(List.of(entries), DEFS, spec -> spec instanceof ItemSpec.Gun);
    }

    @Test
    void flagsAPunchableNonGunWithNoPunchId() {
        PunchAudit.Entry stick = entry("wall-buy", plain("minecraft:stick", PunchResolverTest.pap(true, null, 0)));
        List<PunchAudit.Finding> findings = check(stick);
        assertEquals(List.of(new PunchAudit.Finding(stick, PunchOutcome.RejectReason.NO_UPGRADE_PATH)), findings);
        assertTrue(findings.get(0).message().startsWith("wall-buy: minecraft:stick"), findings.get(0).message());
    }

    @Test
    void flagsAnUnknownPunchId() {
        PunchAudit.Entry typo = entry("box pool", plain("minecraft:stick", PunchResolverTest.pap(true, "mymap:knwon", 0)));
        assertEquals(List.of(new PunchAudit.Finding(typo, PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID)), check(typo));
    }

    @Test
    void validAndUnpunchableItemsAreNotFlagged() {
        assertEquals(List.of(), check(
                entry("a", plain("minecraft:stick", PunchResolverTest.pap(true, "mymap:known", 0))),
                entry("b", ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "ak47"))),
                entry("c", new ItemSpec.Gun(ResourceLocation.tryBuild("tacz", "ak47"), false, false)),
                entry("d", plain("minecraft:iron_sword", null)),
                entry("e", plain("minecraft:stick", PunchResolverTest.pap(false, null, 0)))));
    }
}
