# Wave events

## Goal

Some rounds are special: hellhound-style rounds, fog rounds, all-sprinter rounds. They are data-driven, so packs and maps can add their own.

## Data shape

`data/<ns>/packapunch/wave_events/<id>.json`:

```json
{
  "trigger": { "type": "interval", "every": 5, "start": 5, "jitter": 1 },
  "effects": [
    { "type": "mob_override", "profiles": ["packapunch:hellhound"] },
    { "type": "spawn_rate", "multiplier": 1.5 },
    { "type": "fog", "density": 0.6 },
    { "type": "last_kill_drop", "powerup": "packapunch:max_ammo" }
  ],
  "announce": "wave.packapunch.hounds"
}
```

Trigger types: `interval`, `round` (specific rounds), `chance` (per round). Effect types use codec dispatch.

## Implementation

- **Picking.** At each `Intermission`, `WaveEventRunner` evaluates triggers for the next round with the game's seeded random. At most one wave event per round. Priority goes to `round`, then `interval`, then `chance`.
- **Applying.** For the round, effects modify the `RoundPlan` (count, spawn rate), the profile pool (`mob_override`), and client visuals (`fog`, sent as a packet). `last_kill_drop` forces a power-up on the round's final kill.
- **Events.** Emits `wave_event_started`. Scripts can add custom behavior on top (see `scripting-and-events.md`).
- **Default content.** A "hounds" event using a `minecraft:wolf` mob profile with `ai: replace`. It shows the mob-agnostic system working.

## Config surface

- Per event JSON: trigger, effects, announcement.
- Map override: `wave_events.enabled` list.

## Assets

- Fog uses the vanilla fog renderer through `ViewportEvent.RenderFog`. No texture.

## Depends on

`game-loop-and-rounds.md`, `mob-profiles-and-ai-injection.md`, `power-ups.md`.

## Verification

- JUnit: with a fixed seed, an `interval` trigger fires on the expected rounds, and `jitter` stays within range.
- JUnit: when two triggers match the same round, priority picks the right one.
- In game: round 5 is a hounds round and the last kill drops Max Ammo.

## Phase

6.
