package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.core.BlockPos;
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
 * every player in the game. Played to a player, a stereo sound is heard as is, wherever they turn. The upgrader's fire, the
 * debris burst, and the bleeding heartbeat are played where those happen ({@code PapMachineBlock}, {@link Doors},
 * {@code client/BleedingSound}).
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
    /** The door at three quarters, with the generator (its file fades out from 10 s to silence at 15 s). */
    static final List<Note> POWER_ON = List.of(
            new Note(PapRegistries.POWER_DOOR, 0, 0.75f, 1.0f),
            new Note(PapRegistries.POWER_GENERATOR, 0, 1.0f, 1.0f));
    /** Stands in for 1.20.5's bad omen, which 1.20.1 lacks: the elder guardian's curse, sped up. */
    static final List<Note> POWER_UP_SPAWN = List.of(new Note(PapRegistries.POWER_UP_SPAWN, 0, 1.0f, 1.2f));
    /** Stands in for 1.20.5's raid omen with the raid horn, pitched down, and the ravager's first celebrate. */
    static final List<Note> POWER_UP_GRAB = List.of(
            new Note(PapRegistries.POWER_UP_HORN, 0, 0.6f, 0.9f),
            new Note(PapRegistries.POWER_UP_CELEBRATE, 0, 1.0f, 0.9f));
    static final List<Note> PURCHASE_DENIED = List.of(new Note(PapRegistries.PURCHASE_DENIED, 0, 0.5f, 0.85f));
    /** The cha-ching and the impact. The impact peaks {@link #DEBRIS_PEAK_TICKS} in, when the blocks burst. */
    static final List<Note> DEBRIS_CLEARED = List.of(
            new Note(PapRegistries.CHA_CHING, 0, 1.0f, 1.0f),
            new Note(PapRegistries.DEBRIS_IMPACT, 0, 1.0f, 1.0f));
    /** {@code debris/impact} peaks 0.95 s in. */
    public static final int DEBRIS_PEAK_TICKS = 19;
    static final List<Note> GAME_START = List.of(new Note(PapRegistries.GAME_START, 0, 1.0f, 0.9f));
    static final List<Note> ROUND_START = List.of(new Note(PapRegistries.ROUND_START, 0, 1.0f, 1.0f));
    static final List<Note> ROUND_END = List.of(new Note(PapRegistries.ROUND_END, 0, 1.0f, 1.0f));
    /** Its file is stretched so that at this pitch the whoosh lasts the 3 s the item burns (pap.process_ticks, 60). */
    public static final List<Note> UPGRADER_FIRE = List.of(new Note(PapRegistries.UPGRADER_FIRE, 0, 1.0f, 0.9f));
    static final List<Note> REVIVED = List.of(new Note(PapRegistries.REVIVED, 0, 1.0f, 1.0f));
    static final List<Note> BLED_OUT = List.of(new Note(PapRegistries.BLED_OUT, 0, 0.6f, 0.85f));

    private record Pending(ServerLevel level, long due, Runnable play) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private GameSounds() {}

    static void attach(GameManager.Active active) {
        active.game().events().subscribe(event -> on(active, event));
    }

    private static void on(GameManager.Active active, GameEvent event) {
        if (event instanceof GameEvent.GameStart) {
            everyone(active, GAME_START);
        } else if (event instanceof GameEvent.RoundStart start && start.round() > 1) {
            // Round 1 starts with the game, under the game's own start.
            everyone(active, ROUND_START);
        } else if (event instanceof GameEvent.RoundEnd) {
            everyone(active, ROUND_END);
        } else if (event instanceof GameEvent.PowerOn) {
            everyone(active, POWER_ON);
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
