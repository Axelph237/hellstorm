# Survival foundation (phase 6)

## Goal

The pieces downs, power-ups, traps, and wave events all share, built first so the four features can be written side by side.

## Data shape

- **Settings** (`GameSettings`, server config sections of the same names):
  - `downs`: `bleed_out_ticks` 600, `revive_ticks` 60, `revive_range` 2.5, `solo_self_revives` 3, `regen_delay_ticks` 80, `regen_per_second` 4.0.
  - `powerups`: `drop_chance` 0.03, `max_per_round` 4, `lifetime_ticks` 600, `blink_ticks` 100, `fission_bonus` 400, `pax_romana_bonus` 200.
  - `traps`: `default_cost` 1000, `default_active_ticks` 200, `default_cooldown_ticks` 600.
  - `wave_events`: `enabled` true, `disabled` (ids to leave out).
- **Events** (`GameEvent`, with Lua names and tables in `script/LuaEvents`): `PlayerDowned(player, pos)`, `PlayerRevived(player, by)` (no `by` for a self-revive), `PlayerBledOut(player)`, `PowerUpSpawned(powerUp, pos)`, `PowerUpCollected(powerUp, player)`, `TrapActivated(player, pos)`, `WaveEventStarted(waveEvent, round)` (round filters see its round). Every name in `LuaEvents.NAMES` now has an event.
- **`LifeState.Downed(bleedTicksLeft, reviver, reviveTicks, selfReviving)`** next to `Alive` and `Spectating`.
- **Timed effects** (`Game.startTimed(id, nameKey, ticks)`, `timedActive(id)`): team-wide effects like Insta-Kill. Starting one that's running restarts it. They count down during rounds and intermissions. When one runs out, or the game ends, the world hears `GameWorld.timedEffectEnded(id)` once.
- **HUD** (`GameWorld.Status` and `HudSyncPacket`):
  - `timers`: the running timed effects, drawn across the top as "Insta-Kill 23", blinking grey in the last 5 seconds.
  - `bar`: one player's progress bar (`label`, `progress` 0 to 1, `color`), drawn above the crosshair.
  - `Standing.downed` and `Line.downed`: downed players' points show in red.

## Verification

- JUnit: `GameTest.timedEffectsCountDownShowOnTheHudAndEndOnce`, `gameOverEndsRunningTimedEffects`; `LuaEventsTest` (every event type has a name, the survival events' fields); `PacketRoundTripTest.hud` with timers, a bar, and a downed row.

## Phase

6.
