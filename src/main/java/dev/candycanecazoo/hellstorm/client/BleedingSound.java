package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The heartbeat a downed player hears, looping until they're revived or bleed out. Only they hear it, as is: it's played
 * relative to the listener, with no falloff. Its file loops from just before one beat to just before the matching one.
 */
public final class BleedingSound extends AbstractTickableSoundInstance {
    private static BleedingSound playing;

    private BleedingSound() {
        super(PapRegistries.BLEEDING.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        looping = true;
        relative = true;
        attenuation = Attenuation.NONE;
    }

    /** Starts the heartbeat if this player is down and it isn't already playing; it stops itself once they aren't. */
    static void update() {
        if (HudState.selfDowned() && (playing == null || playing.isStopped())) {
            playing = new BleedingSound();
            Minecraft.getInstance().getSoundManager().play(playing);
        }
    }

    @Override
    public void tick() {
        if (!HudState.selfDowned()) {
            stop();
        }
    }
}
