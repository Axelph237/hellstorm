# Configuration

## Goal

Every tunable value lives in one place and resolves the same way. Adding a new configurable value later means adding one field.

## Data shape

Three layers resolve into one immutable record:

1. **Per-map override.** Stored with the map in `MapSavedData` and in exported map JSON.
2. **Server config.** `ForgeConfigSpec` of type SERVER, stored per world in `serverconfig/packapunch-server.toml` and synced to clients.
3. **Built-in defaults.** Constants in code.

```java
record GameSettings(
    RoundSettings rounds,        // zombie count, health, speed, max alive, spawn delay curves
    EconomySettings economy,     // points per hit, kill, headshot, melee, repair
    PlayerSettings players,      // bleed-out time, revive time, perk limit, weapon slots, regen
    WindowSettings windows,      // max width, max height, default boards
    ScriptSettings scripting,    // command permission level, instruction budget, error limit
    TaczSettings tacz) { ... }
```

Each sub-record has a `Codec`. The same codec reads map-override JSON, writes NBT, and syncs to clients.

Content definitions are separate from settings. They are datapack JSON under `data/<ns>/packapunch/`:
- `mob_profiles/`, `perks/`, `powerups/`, `wave_events/`, `box_pools/`, `weapon_tiers/`, `punch/`, `events/`, `scripts/`

## Implementation

- `config/ServerConfig` builds the `ForgeConfigSpec`.
- `config/SettingsResolver.resolve(mapOverride, serverConfig)` returns a `GameSettings`. For each field it takes the map override if present, else the server config value, else the default. It is a pure function with no Minecraft dependencies.
- A game resolves its `GameSettings` once at game start and keeps it for the whole game.
- `data/` registers one `SimpleJsonResourceReloadListener` per content type through `AddReloadListenerEvent`, so `/reload` picks up edits. Each listener parses with its codec and logs per-file errors without dropping the other files.
- Content that clients need (perk names and icons, power-up types) is synced with a packet after login and after `/reload`.

## Config surface

This feature is the config surface. Each other feature file lists its own keys.

## Assets

None.

## Depends on

Nothing. Everything else depends on this.

## Verification

- JUnit on `SettingsResolver`: a map override beats the server config, and the server config beats the default, for every field.
- JUnit codec round-trips: `GameSettings` to JSON and back, and to NBT and back, are equal.
- In game: change a perk JSON, run `/reload`, and see the new value in the next purchase.

## Phase

0 (skeleton), grows in every phase.

## Debug cheats

`/pap debug infinite_money <true|false> [players]` and `/pap debug god <true|false> [players]`, op only. They default to the player running the command. Infinite money makes purchases cost nothing and never fail for lack of points. God mode cancels all damage, in or out of a game. They're kept in memory per player (`game/Cheats`) and cleared by a server restart.
