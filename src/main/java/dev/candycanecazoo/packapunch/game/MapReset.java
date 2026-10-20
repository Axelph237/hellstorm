package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.feature.MysteryBoxes;
import dev.candycanecazoo.packapunch.feature.PapMachineBlock;
import dev.candycanecazoo.packapunch.feature.TrapControllerBlock;
import dev.candycanecazoo.packapunch.feature.PowerSwitchBlock;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.server.level.ServerLevel;

/** Puts every feature back the way a fresh game expects it. Safe to run any number of times. */
public final class MapReset {
    private MapReset() {}

    public static void reset(ServerLevel level, ZombiesMap map) {
        Doors.restore(level);
        WindowOps.resetAll(level, map);
        PowerSwitchBlock.setAll(level, map.features(FeatureKind.POWER_SWITCH), false);
        MysteryBoxes.resetAll(level, map.features(FeatureKind.MYSTERY_BOX));
        PapMachineBlock.resetAll(level, map.features(FeatureKind.PAP_MACHINE));
        TrapControllerBlock.resetAll(level, map.features(FeatureKind.TRAP));
        level.getEntities(dev.candycanecazoo.packapunch.powerup.PowerUpRegistries.POWER_UP.get(), e -> true).forEach(net.minecraft.world.entity.Entity::discard);
    }
}
