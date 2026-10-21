package dev.candycanecazoo.hellstorm.pap;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Decides what Pack-a-Punch does with an item, from its NBT alone. Never changes the tag it is given. */
public final class PunchResolver {
    /** {@code pap.tier}: how many default upgrades a TacZ gun has had. */
    public static final String TIER = "tier";

    private PunchResolver() {}

    /**
     * @param tag         the item's NBT, or null
     * @param isTaczGun   whether the item is a TacZ gun, which can take the default upgrade
     * @param defaultCost the cost when the {@link PunchDef} names none, and of the default upgrade
     * @param maxTier     how many default upgrades a gun can have
     */
    public static PunchOutcome resolve(@Nullable CompoundTag tag, boolean isTaczGun, Map<ResourceLocation, PunchDef> defs, int defaultCost,
                                       int maxTier) {
        CompoundTag pap = tag == null ? new CompoundTag() : tag.getCompound(ItemSpec.PAP);
        if (!pap.getBoolean(ItemSpec.PUNCHABLE)) {
            return new PunchOutcome.Rejected(PunchOutcome.RejectReason.NOT_PUNCHABLE);
        }
        if (pap.contains(ItemSpec.PUNCH_ID)) {
            ResourceLocation id = ResourceLocation.tryParse(pap.getString(ItemSpec.PUNCH_ID));
            PunchDef def = id == null ? null : defs.get(id);
            return def == null
                    ? new PunchOutcome.Rejected(PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID)
                    : new PunchOutcome.Replace(id, def, def.cost().orElse(defaultCost));
        }
        if (!isTaczGun) {
            return new PunchOutcome.Rejected(PunchOutcome.RejectReason.NO_UPGRADE_PATH);
        }
        if (pap.getInt(TIER) >= maxTier) {
            return new PunchOutcome.Rejected(PunchOutcome.RejectReason.MAX_TIER);
        }
        return new PunchOutcome.UpgradeTacz(defaultCost);
    }
}
