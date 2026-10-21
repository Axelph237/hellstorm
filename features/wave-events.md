# Wave events

## Goal

Some rounds are special: hellhound-style rounds, fog rounds, all-sprinter rounds. They are data-driven, so packs and maps can add their own.

## Data shape

`data/<ns>/hellstorm/wave_events/<id>.json`:

```json
{
  "trigger": { "type": "interval", "every": 5, "start": 5, "jitter": 1 },
  "effects": [
    { "type": "mob_override", "profiles": ["hellstorm:hellhound"] },
    { "type": "spawn_rate", "multiplier": 1.5 },
    { "type": "fog", "density": 0.6 },
    { "type": "last_kill_drop", "powerup": "hellstorm:max_ammo" }
  ],
  "announce": "wave.hellstorm.hounds"
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

## As built

- **Definitions** (`wave/WaveEventDef`, loaded by `wave/WaveEventDefs` from `data/<ns>/hellstorm/wave_events/`): `trigger`, `effects`, and an optional `announce` lang key.
  - Triggers (`wave/WaveTrigger`): `{"type": "round", "rounds": [...]}`, `{"type": "interval", "every", "start" (1), "jitter" (0)}`, `{"type": "chance", "chance", "from_round" (1)}`.
  - Effects (`wave/WaveEffect`): `mob_override` (`profiles`), `spawn_rate` (`multiplier`), `count` (`multiplier`, added so a special round can be smaller or bigger), `fog` (`density` 0 to 1), `last_kill_drop` (`powerup`).
- **Picking** (`wave/WaveSchedule`, pure): one per game, built from the game's content and `wave_events.enabled` / `disabled`, using the game's random. Asked once per round at round start. `round` triggers win, then `interval`, then `chance`; ties go to the lowest id. Interval occurrence *n* lands within `jitter` of `start + n * every` and always after the one before; an occurrence beaten by a higher-priority event is used up, so the rhythm holds. Chance rolls only happen when nothing else matched.
- **Applying** (`wave/RoundRules`, folded from the effects; multipliers multiply). In `Game.beginRound`, after the round counters reset: emit `wave_event_started`, announce the key in red (`GameWorld.announceKey`, translated on each client), send the fog, then `round_start` as usual. The round's count is multiplied by `count`; the spawn delay is divided by `spawn_rate`; zombies come only from the override profiles that fit the marker's window (any weight, so weight-0 profiles are special-round-only), else the normal roster. When the round's last zombie dies, `last_kill_drop` is queued like any drop, whoever made the kill and regardless of the per-round cap. Fog clears when the round ends and at game over. `Game.waveEvent()` reports the current round's rules.
- **Fog** (`net/FogPacket`, the last packet in `PapNetwork`; `client/WaveFog`): density 0 clears it. `ViewportEvent.RenderFog` pulls the far plane to `render distance × (1 − density)²`, never nearer than 6 blocks. Cleared on logout.
- **Default content.** `hellstorm:hounds`: every 5 rounds from round 5, only `hellstorm:hellhound` (a new weight-0 mob profile: `minecraft:wolf`, `ai: replace`, a third of a zombie's health that round (`health_multiplier` 0.33), 1.5× speed, 3 damage), 0.75× the zombies, 1.5× spawn rate, fog 0.6, and Max Ammo on the last kill. Announced as "Something is coming. The hounds are loose."
- **Tests.** JUnit `WaveScheduleTest`: an interval without jitter fires on 5, 10, 15, 20; with jitter 1 every occurrence is within a round of its nominal one, a seed repeats, and some seed moves one; round beats interval beats chance, and the beaten interval keeps its rhythm; ties go to the lowest id; `disabled` and `enabled: false`; the shipped hounds file folds into its rules. JUnit `game/WaveGameTest`: round 1 is normal, round 2 (a `round` trigger) emits `wave_event_started` before `round_start`, announces, sets fog, spawns half the zombies and only hounds; the last kill (by nobody) drops Max Ammo at drop chance 0, and the fog lifts with the round. GameTest `aHoundHasAThirdOfAZombiesHealth`: in round 5 a hound spawned by the game has 0.33 of a zombie's max health.
- **Not verified in game:** the fog's look, and wolves as hounds through real windows (the AI is the same injected AI as zombies; wolves are a supported `PathfinderMob`). A player who leaves the game mid-round keeps the fog until the round ends for everyone else or they log out.
