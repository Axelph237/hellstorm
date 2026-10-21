package dev.candycanecazoo.hellstorm.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A fake player that keeps the sounds played to it alone, which a fake player's connection would drop. */
final class ListeningPlayer extends FakePlayer {
    final List<SoundEvent> heard = new ArrayList<>();

    ListeningPlayer(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.randomUUID(), name));
    }

    @Override
    public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
        heard.add(sound);
    }
}
