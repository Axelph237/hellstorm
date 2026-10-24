package dev.candycanecazoo.hellstorm.pap;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PunchAuditTest {
    private static final ResourceLocation KNOWN = ResourceLocation.tryBuild("mymap", "known");
    private static final ResourceLocation AK = ResourceLocation.tryBuild("tacz", "ak47");
    private static final PunchDef SWORD = new PunchDef(ResourceLocation.tryBuild("minecraft", "diamond_sword"), Optional.empty(), Optional.empty(),
            Optional.empty(), PunchDef.CarryOver.NONE);
    private static final Map<ResourceLocation, PunchDef> DEFS = Map.of(KNOWN, SWORD);

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

    private static List<PunchAudit.Kind> kinds(List<PunchAudit.Finding> findings) {
        return findings.stream().map(PunchAudit.Finding::kind).toList();
    }

    @Test
    void flagsAPunchableNonGunWithNoPunchId() {
        PunchAudit.Entry stick = entry("wall-buy", plain("minecraft:stick", PunchResolverTest.pap(true, null, 0)));
        List<PunchAudit.Finding> findings = check(stick);
        assertEquals(List.of(PunchAudit.Kind.NO_UPGRADE_PATH), kinds(findings));
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), findings.get(0).pos());
        assertTrue(findings.get(0).message().startsWith("wall-buy: minecraft:stick"), findings.get(0).message());
    }

    @Test
    void flagsAnUnknownPunchId() {
        PunchAudit.Entry typo = entry("box pool", plain("minecraft:stick", PunchResolverTest.pap(true, "mymap:knwon", 0)));
        assertEquals(List.of(PunchAudit.Kind.UNKNOWN_PUNCH_ID), kinds(check(typo)));
    }

    @Test
    void flagsAGunWhosePunchIdInItsNbtIsUnknown() {
        CompoundTag nbt = PunchResolverTest.pap(true, "mymap:ak_tier3", 0);
        assertEquals(List.of(PunchAudit.Kind.UNKNOWN_PUNCH_ID), kinds(check(entry("upgrade definition mymap:ak_tier2",
                new ItemSpec.Gun(AK, false, true, Optional.of(nbt))))));
        assertEquals(List.of(), check(entry("ok", new ItemSpec.Gun(AK, false, true, Optional.of(PunchResolverTest.pap(true, "mymap:known", 0))))));
    }

    @Test
    void validAndUnpunchableItemsAreNotFlagged() {
        assertEquals(List.of(), check(
                entry("a", plain("minecraft:stick", PunchResolverTest.pap(true, "mymap:known", 0))),
                entry("b", ItemSpec.Gun.of(AK)),
                entry("c", new ItemSpec.Gun(AK, false, false)),
                entry("d", plain("minecraft:iron_sword", null)),
                entry("e", plain("minecraft:stick", PunchResolverTest.pap(false, null, 0)))));
    }

    @Test
    void flagsSkippedEntriesMissingResultsAndUnknownGunClasses() {
        ResourceLocation typo = ResourceLocation.tryBuild("mymap", "typo");
        PunchDef missing = new PunchDef(ResourceLocation.tryBuild("minecraft", "diamond_swrod"), Optional.empty(), Optional.empty(), Optional.empty(),
                PunchDef.CarryOver.NONE);
        PunchDefs.Loaded loaded = new PunchDefs.Loaded(Map.of(KNOWN, SWORD, typo, missing),
                Map.of("pistol", new GunDefault(new CompoundTag(), GunDefault.Method.MERGE), "riffle", new GunDefault(new CompoundTag(), GunDefault.Method.MERGE)),
                List.of("mymap:upgrades, defaults \"smg\": method: Unknown method \"replace\": use merge, modify or remove"));
        List<PunchAudit.Finding> findings = PunchAudit.checkFiles(loaded, id -> id.getPath().equals("diamond_sword"), Set.of("pistol", "rifle"));
        assertEquals(List.of(PunchAudit.Kind.BAD_ENTRY, PunchAudit.Kind.MISSING_RESULT, PunchAudit.Kind.UNKNOWN_GUN_CLASS), kinds(findings));
        assertEquals("upgrade file mymap:upgrades, defaults \"smg\": method: Unknown method \"replace\": use merge, modify or remove", findings.get(0).message());
        assertEquals("upgrade definition mymap:typo: its item-id minecraft:diamond_swrod is not an installed item or TacZ gun", findings.get(1).message());
        assertEquals("upgrade defaults: \"riffle\" is not the class of any installed TacZ gun (they are pistol, rifle)", findings.get(2).message());
        assertEquals(List.of(), PunchAudit.checkFiles(PunchDefs.Loaded.EMPTY, id -> false, Set.of()));
    }

    @Test
    void opNoticeIsSilentWhenAllIsWellAndListsTheFirstFiveOtherwise() {
        assertEquals(List.of(), PunchAudit.opNotice(List.of()));
        PunchAudit.Entry stick = entry("wall-buy", plain("minecraft:stick", PunchResolverTest.pap(true, null, 0)));
        List<PunchAudit.Finding> seven = java.util.Collections.nCopies(7, PunchAudit.Finding.of(stick, PunchOutcome.RejectReason.NO_UPGRADE_PATH));
        List<String> lines = PunchAudit.opNotice(seven).stream().map(net.minecraft.network.chat.Component::getString).toList();
        assertEquals(7, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(0).contains("7 problems"), lines.get(0));
        assertTrue(lines.get(1).contains("minecraft:stick") && lines.get(1).endsWith("at 1, 2, 3"), lines.get(1));
        assertEquals("...and 2 more. See /hs map validate", lines.get(6));
        List<String> one = PunchAudit.opNotice(seven.subList(0, 1)).stream().map(net.minecraft.network.chat.Component::getString).toList();
        assertEquals(List.of("1 problem", "See /hs map validate"), List.of(one.get(0).replaceAll(".*found (1 problem).*", "$1"), one.get(2)));
    }
}
