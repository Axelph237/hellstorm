package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The game's sounds for its events, each a cue of notes. A cue plays at a place (heard by those nearby), to one player, or to
 * every player in the game. Played to a player, a stereo sound is heard as is, wherever they turn. A sound for using a block
 * plays at the block, where those happen: the power switch, the upgrader, and bought doors ({@code PowerSwitchBlock},
 * {@code PapMachineBlock}, {@link Doors}). The bleeding heartbeat plays on the downed player's client
 * ({@code client/BleedingSound}).
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class GameSounds {
    /** One sound of a cue, {@code delay} ticks after the cue starts. */
    record Note(Supplier<SoundEvent> sound, int delay, float volume, float pitch) {}

    /** Wither death, the crate's laugh, and soul cries rising and falling in pitch. */
    static final List<Note> BOX_MOVE = List.of(
            new Note(() -> SoundEvents.WITHER_DEATH, 0, 1.0f, 1.0f),
            new Note(PapRegistries.MYSTERY_BOX_LAUGH, 4, 1.0f, 1.0f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 0, 0.8f, 0.7f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 6, 0.8f, 0.9f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 12, 0.8f, 1.15f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 18, 0.8f, 1.4f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 26, 0.8f, 1.15f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 34, 0.8f, 0.9f),
            new Note(PapRegistries.MYSTERY_BOX_SOULS, 42, 0.8f, 0.7f));
    /** At the switch: the door at three quarters, with the generator (its file fades out from 10 s to silence at 15 s). */
    public static final List<Note> POWER_ON = List.of(
            new Note(PapRegistries.POWER_DOOR, 0, 0.75f, 1.0f),
            new Note(PapRegistries.POWER_GENERATOR, 0, 1.0f, 1.0f));
    /** Stands in for 1.20.5's bad omen, which 1.20.1 lacks: the elder guardian's curse, sped up. */
    static final List<Note> POWER_UP_SPAWN = List.of(new Note(PapRegistries.POWER_UP_SPAWN, 0, 0.5f, 1.2f));
    /** The ender dragon's fourth growl and the ravager's first celebrate, both pitched down. */
    static final List<Note> POWER_UP_GRAB = List.of(
            new Note(PapRegistries.POWER_UP_DRAGON, 0, 0.5f, 0.9f),
            new Note(PapRegistries.POWER_UP_CELEBRATE, 0, 0.5f, 0.9f));
    static final List<Note> PURCHASE_DENIED = List.of(new Note(PapRegistries.PURCHASE_DENIED, 0, 0.5f, 0.85f));
    /** A door or debris bought: the cha-ching and the impact. The impact peaks {@link #IMPACT_PEAK_TICKS} in, when the blocks burst. */
    static final List<Note> DOOR_OPENED = List.of(
            new Note(PapRegistries.CHA_CHING, 0, 1.0f, 1.0f),
            new Note(PapRegistries.DEBRIS_IMPACT, 0, 1.0f, 1.0f));
    /** {@code debris/impact} peaks 0.95 s in. */
    public static final int IMPACT_PEAK_TICKS = 19;
    static final List<Note> GAME_START = List.of(new Note(PapRegistries.GAME_START, 0, 1.0f, 0.9f));
    static final List<Note> ROUND_START = List.of(new Note(PapRegistries.ROUND_START, 0, 0.75f, 1.0f));
    static final List<Note> ROUND_END = List.of(new Note(PapRegistries.ROUND_END, 0, 1.0f, 1.0f));
    /**
     * An item going into the upgrader: the anvil, pitched down and so slowed, and the fire, whose file is stretched so that at
     * this pitch the whoosh lasts the 3 s the item burns (pap.process_ticks, 60).
     */
    public static final List<Note> UPGRADER_START = List.of(
            new Note(() -> SoundEvents.ANVIL_USE, 0, 1.0f, 0.6f),
            new Note(PapRegistries.UPGRADER_FIRE, 0, 1.0f, 0.9f));
    static final List<Note> REVIVED = List.of(new Note(PapRegistries.REVIVED, 0, 1.0f, 1.0f));
    static final List<Note> BLED_OUT = List.of(new Note(PapRegistries.BLED_OUT, 0, 0.6f, 0.85f));

    /** A long track played quietly now and then through a game, {@code ticks} long, at a volume that evens out their loudness. */
    public record Track(Supplier<SoundEvent> sound, int ticks, float volume) {}

    /** Their volumes put each at the loudness of the quietest, Alone in the Dark, at half volume. */
    public static final List<Track> AMBIENT = List.of(
            new Track(PapRegistries.AMBIENT_ALONE_IN_THE_DARK, 127 * 20, 0.5f),
            new Track(PapRegistries.AMBIENT_EYES_IN_THE_SKY, 185 * 20, 0.26f),
            new Track(PapRegistries.AMBIENT_KEEPER_OF_SECRETS, 12 * 20, 0.15f),
            new Track(PapRegistries.AMBIENT_LUMINOUS_LIFE, 122 * 20, 0.066f));
    /** The first track comes this long into a game, give or take {@link #AMBIENT_JITTER_TICKS}; each next one this long after the last ends. */
    public static final int AMBIENT_GAP_TICKS = 90 * 20;
    public static final int AMBIENT_JITTER_TICKS = 45 * 20;

    private record Pending(ServerLevel level, long due, Runnable play) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private GameSounds() {}

    static void attach(GameManager.Active active) {
        active.game().events().subscribe(event -> on(active, event));
    }

    private static void on(GameManager.Active active, GameEvent event) {
        if (event instanceof GameEvent.GameStart) {
            everyone(active, GAME_START);
            nextAmbient(active, null);
        } else if (event instanceof GameEvent.GameEnd) {
            active.game().players().forEach(state -> active.world().entity(state.id()).ifPresent(GameSounds::stopAmbient));
        } else if (event instanceof GameEvent.RoundStart start && start.round() > 1) {
            // Round 1 starts with the game, under the game's own start.
            everyone(active, ROUND_START);
        } else if (event instanceof GameEvent.RoundEnd) {
            everyone(active, ROUND_END);
        } else if (event instanceof GameEvent.MysteryBoxMoved moved) {
            at(active.level(), moved.from(), BOX_MOVE);
        } else if (event instanceof GameEvent.PowerUpSpawned spawned) {
            at(active.level(), spawned.pos(), POWER_UP_SPAWN);
        } else if (event instanceof GameEvent.PowerUpCollected) {
            everyone(active, POWER_UP_GRAB);
        } else if (event instanceof GameEvent.PlayerRevived revived) {
            player(active, revived.player(), REVIVED);
        } else if (event instanceof GameEvent.PlayerBledOut) {
            everyone(active, BLED_OUT);
        }
    }

    /** After a gap, plays a track other than {@code last}, while {@code active} is still this level's game. */
    private static void nextAmbient(GameManager.Active active, Track last) {
        ServerLevel level = active.level();
        int gap = AMBIENT_GAP_TICKS + level.random.nextIntBetweenInclusive(-AMBIENT_JITTER_TICKS, AMBIENT_JITTER_TICKS);
        PENDING.add(new Pending(level, level.getGameTime() + gap, () -> {
            if (GameManager.get(level).filter(game -> game == active).isPresent()) {
                playAmbient(active, last);
            }
        }));
    }

    /** Plays a track other than {@code last} to everyone in the game, and the next after it ends and a gap. */
    public static void playAmbient(GameManager.Active active, Track last) {
        ServerLevel level = active.level();
        List<Track> others = AMBIENT.stream().filter(track -> track != last).toList();
        Track track = others.get(level.random.nextInt(others.size()));
        active.game().players().forEach(state -> active.world().entity(state.id()).ifPresent(player ->
                player.playNotifySound(track.sound().get(), SoundSource.AMBIENT, track.volume(), 1.0f)));
        PENDING.add(new Pending(level, level.getGameTime() + track.ticks(), () -> nextAmbient(active, track)));
    }

    /** Stops any ambient track {@code player} is hearing, when the game ends or they leave it. */
    public static void stopAmbient(ServerPlayer player) {
        AMBIENT.forEach(track -> player.connection.send(new ClientboundStopSoundPacket(track.sound().get().getLocation(), SoundSource.AMBIENT)));
    }

    /** Heard by those near {@code pos}. */
    public static void at(ServerLevel level, BlockPos pos, List<Note> cue) {
        play(level, cue, note -> level.playSound(null, pos, note.sound().get(), SoundSource.BLOCKS, note.volume(), note.pitch()));
    }

    /** Heard by {@code player} alone. */
    public static void to(ServerPlayer player, List<Note> cue) {
        play(player.serverLevel(), cue, note -> player.playNotifySound(note.sound().get(), SoundSource.PLAYERS, note.volume(), note.pitch()));
    }

    private static void player(GameManager.Active active, UUID id, List<Note> cue) {
        active.world().entity(id).ifPresent(player -> to(player, cue));
    }

    /** Heard by every player in the game who is online. */
    private static void everyone(GameManager.Active active, List<Note> cue) {
        active.game().players().forEach(state -> player(active, state.id(), cue));
    }

    private static void play(ServerLevel level, List<Note> cue, Consumer<Note> sound) {
        for (Note note : cue) {
            if (note.delay() == 0) {
                sound.accept(note);
            } else {
                PENDING.add(new Pending(level, level.getGameTime() + note.delay(), () -> sound.accept(note)));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !PENDING.isEmpty()) {
            List<Pending> due = PENDING.stream().filter(p -> p.level().getGameTime() >= p.due()).toList();
            PENDING.removeAll(due);
            due.forEach(p -> p.play().run());
        }
    }
}
