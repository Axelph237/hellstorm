package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.game.GameEvent;
import net.minecraft.core.BlockPos;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Event names scripts can listen for, and the Lua table each event arrives as. */
public final class LuaEvents {
    /** The full catalogue from the plan. Later phases fire the ones not produced yet, so scripts can be written against them now. */
    public static final Set<String> NAMES = Set.of(
            "game_start", "game_end", "round_start", "round_end",
            "zombie_spawned", "zombie_killed",
            "player_downed", "player_revived", "player_bled_out",
            "wall_buy", "mystery_box_opened", "mystery_box_moved", "perk_bought", "pap_used",
            "door_opened", "power_on", "trap_activated",
            "powerup_spawned", "powerup_collected",
            "wave_event_started");

    private LuaEvents() {}

    public static String name(GameEvent event) {
        if (event instanceof GameEvent.GameStart) return "game_start";
        if (event instanceof GameEvent.GameEnd) return "game_end";
        if (event instanceof GameEvent.RoundStart) return "round_start";
        if (event instanceof GameEvent.RoundEnd) return "round_end";
        if (event instanceof GameEvent.ZombieSpawned) return "zombie_spawned";
        if (event instanceof GameEvent.ZombieKilled) return "zombie_killed";
        if (event instanceof GameEvent.PowerOn) return "power_on";
        if (event instanceof GameEvent.DoorOpened) return "door_opened";
        if (event instanceof GameEvent.PerkBought) return "perk_bought";
        if (event instanceof GameEvent.WallBuy) return "wall_buy";
        if (event instanceof GameEvent.MysteryBoxOpened) return "mystery_box_opened";
        if (event instanceof GameEvent.MysteryBoxMoved) return "mystery_box_moved";
        if (event instanceof GameEvent.PapUsed) return "pap_used";
        throw new IllegalArgumentException("No script name for " + event);
    }

    /**
     * The round an event is about, for round filters. Round events say which round they mean: {@code round_start} fires just
     * before the game enters that round, so the game's current round would still be the previous one.
     */
    public static java.util.OptionalInt round(GameEvent event) {
        if (event instanceof GameEvent.RoundStart e) return java.util.OptionalInt.of(e.round());
        if (event instanceof GameEvent.RoundEnd e) return java.util.OptionalInt.of(e.round());
        if (event instanceof GameEvent.GameEnd e) return java.util.OptionalInt.of(e.roundReached());
        return java.util.OptionalInt.empty();
    }

    /** A fresh table per call, so one listener changing it can't affect the next. */
    public static LuaTable table(GameEvent event) {
        LuaTable t = new LuaTable();
        t.set("event", name(event));
        if (event instanceof GameEvent.GameStart e) {
            t.set("players", uuids(e.players()));
        } else if (event instanceof GameEvent.GameEnd e) {
            t.set("round", e.roundReached());
        } else if (event instanceof GameEvent.RoundStart e) {
            t.set("round", e.round());
        } else if (event instanceof GameEvent.RoundEnd e) {
            t.set("round", e.round());
        } else if (event instanceof GameEvent.ZombieSpawned e) {
            t.set("mob", e.mob().toString());
            pos(t, e.pos());
        } else if (event instanceof GameEvent.DoorOpened e) {
            t.set("door", e.door());
            e.by().ifPresent(p -> t.set("player", p.toString()));
            LuaTable zones = new LuaTable();
            for (int i = 0; i < e.unlocked().size(); i++) {
                zones.set(i + 1, e.unlocked().get(i).toString());
            }
            t.set("unlocked", zones);
        } else if (event instanceof GameEvent.PowerOn e) {
            e.by().ifPresent(p -> t.set("player", p.toString()));
        } else if (event instanceof GameEvent.PerkBought e) {
            t.set("player", e.player().toString());
            t.set("perk", e.perk().toString());
        } else if (event instanceof GameEvent.ZombieKilled e) {
            t.set("mob", e.mob().toString());
            e.killer().ifPresent(k -> t.set("killer", k.toString()));
            t.set("headshot", LuaValue.valueOf(e.headshot()));
            t.set("melee", LuaValue.valueOf(e.melee()));
            pos(t, e.pos());
        } else if (event instanceof GameEvent.WallBuy e) {
            t.set("player", e.player().toString());
            t.set("item", e.item());
            t.set("cost", e.cost());
            t.set("refill", LuaValue.valueOf(e.refill()));
        } else if (event instanceof GameEvent.MysteryBoxOpened e) {
            t.set("player", e.player().toString());
            t.set("item", e.item());
            pos(t, e.pos());
        } else if (event instanceof GameEvent.MysteryBoxMoved e) {
            pos(t, e.from());
            LuaTable to = new LuaTable();
            pos(to, e.to());
            t.set("to", to);
        } else if (event instanceof GameEvent.PapUsed e) {
            t.set("player", e.player().toString());
            t.set("item", e.item());
        }
        return t;
    }

    private static LuaTable uuids(List<UUID> ids) {
        LuaTable list = new LuaTable();
        for (int i = 0; i < ids.size(); i++) {
            list.set(i + 1, ids.get(i).toString());
        }
        return list;
    }

    private static void pos(LuaTable t, BlockPos pos) {
        t.set("x", pos.getX());
        t.set("y", pos.getY());
        t.set("z", pos.getZ());
    }
}
