package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps vanilla's music from playing over a game's ambient track ({@link GameSounds#AMBIENT}), which plays on the music
 * channel too. A track starting stops vanilla's song, and any song vanilla starts while a track plays is dropped. Vanilla
 * then waits its usual gap (10 to 20 minutes in the overworld) before it tries again.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
public final class AmbientMusic {
    private static SoundInstance track;

    private AmbientMusic() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (GameSounds.AMBIENT.stream().anyMatch(t -> t.sound().get().getLocation().equals(sound.getLocation()))) {
            track = sound;
            minecraft.getMusicManager().stopPlaying();
        } else if (track != null && minecraft.getSoundManager().isActive(track)) {
            event.setSound(null);
        }
    }
}
