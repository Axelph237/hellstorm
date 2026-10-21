package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Phase 0 spike: overrides RPM on stacks tagged by {@link TaczSpikeTests}. Replaced by the real PaP hook in phase 5. */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class SpikeRpmOverride {
    static final String TAG = "hellstorm_spike_rpm";

    private SpikeRpmOverride() {}

    @SubscribeEvent
    public static void onProperties(AttachmentPropertyEvent event) {
        CompoundTag tag = event.getGunItem().getTag();
        if (tag != null && tag.contains(TAG)) {
            event.getCacheProperty().setCache(GunProperties.ROUNDS_PER_MINUTE, tag.getInt(TAG));
        }
    }
}
