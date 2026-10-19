package dev.candycanecazoo.packapunch.gametest;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Phase 0 spike: overrides RPM on stacks tagged by {@link TaczSpikeTests}. Replaced by the real PaP hook in phase 5. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class SpikeRpmOverride {
    static final String TAG = "packapunch_spike_rpm";

    private SpikeRpmOverride() {}

    @SubscribeEvent
    public static void onProperties(AttachmentPropertyEvent event) {
        CompoundTag tag = event.getGunItem().getTag();
        if (tag != null && tag.contains(TAG)) {
            event.getCacheProperty().setCache(GunProperties.ROUNDS_PER_MINUTE, tag.getInt(TAG));
        }
    }
}
