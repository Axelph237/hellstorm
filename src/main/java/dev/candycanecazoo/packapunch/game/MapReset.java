package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import dev.candycanecazoo.packapunch.feature.PowerSwitchBlock;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.server.level.ServerLevel;

/** Puts every feature back the way a fresh game expects it. Safe to run any number of times. */
public final class MapReset {
    private MapReset() {}

    public static void reset(ServerLevel level, ZombiesMap map) {
        WindowOps.resetAll(level, map);
        PowerSwitchBlock.setAll(level, map.features(FeatureKind.POWER_SWITCH), false);
        PapMachineBlock.resetAll(level, map.features(FeatureKind.PAP_MACHINE));
    }
}
