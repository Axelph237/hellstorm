package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.powerup.PowerUpDrops;
import dev.candycanecazoo.packapunch.wave.WaveEventDef;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Datapack content a game takes when it starts, as plain data: what power-ups can drop, and the wave events. */
public record SurvivalContent(PowerUpDrops.Table powerUps, Map<ResourceLocation, WaveEventDef> waveEvents) {
    public static final SurvivalContent NONE = new SurvivalContent(PowerUpDrops.Table.EMPTY, Map.of());

    public SurvivalContent {
        waveEvents = Map.copyOf(waveEvents);
    }
}
