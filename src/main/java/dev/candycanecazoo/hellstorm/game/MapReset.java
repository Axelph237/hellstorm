package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.feature.MysteryBoxes;
import dev.candycanecazoo.hellstorm.feature.PapMachineBlock;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import dev.candycanecazoo.hellstorm.feature.PowerSwitchBlock;
import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import net.minecraft.server.level.ServerLevel;

/** Puts every feature back the way a fresh game expects it. Safe to run any number of times. */
public final class MapReset {
    private MapReset() {}

    public static void reset(ServerLevel level, ZombiesMap map) {
        DoorFlights.clear(level);
        Doors.restore(level);
        WindowOps.resetAll(level, map);
        PowerSwitchBlock.setAll(level, map.features(FeatureKind.POWER_SWITCH), false);
        MysteryBoxes.resetAll(level, map.features(FeatureKind.MYSTERY_BOX));
        PapMachineBlock.resetAll(level, map.features(FeatureKind.PAP_MACHINE));
        VendingMachineBlock.resetAll(level, map.features(FeatureKind.VENDING_MACHINE));
        TrapControllerBlock.resetAll(level, map.features(FeatureKind.TRAP));
        level.getEntities(dev.candycanecazoo.hellstorm.powerup.PowerUpRegistries.POWER_UP.get(), e -> true).forEach(net.minecraft.world.entity.Entity::discard);
    }
}
