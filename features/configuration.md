# Configuration

## Goal

Every tunable value lives in one place and resolves the same way. Adding a new configurable value later means adding one field.

## Data shape

Three layers resolve into one immutable record:

1. **Per-map override.** Stored with the map in `MapSavedData` and in exported map JSON.
2. **Server config.** `ForgeConfigSpec` of type SERVER, stored per world in `serverconfig/hellstorm-server.toml` and synced to clients.
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

Content definitions are separate from settings. They are datapack JSON under `data/<ns>/hellstorm/`:
- `mob_profiles/`, `powerups/`, `wave_events/`, `box_pools/`, `weapon_tiers/`, `punch/`, `events/`, `scripts/`

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
- In game: change a power-up JSON, run `/reload`, and see the new value on the next drop.

## Phase

0 (skeleton), grows in every phase.

## Debug cheats

`/hs debug infinite_money <true|false> [players]` and `/hs debug god <true|false> [players]`, op only. They default to the player running the command. Infinite money makes purchases cost nothing and never fail for lack of points. God mode cancels all damage, in or out of a game. They're kept in memory per player (`game/Cheats`) and cleared by a server restart.

## Added in phase 7

- `players.max_players` (4): most players in one game.
- `players.reconnect_grace_ticks` (1200): how long a player who disconnects mid-game keeps their place, points, and perks (see `sessions-and-multiplayer.md`).
- `players.knockback_reduction` (0.85): how much knockback a player in a game is spared, 0 to 1. At 0.85 they're pushed 15% as far as usual. It scales `LivingKnockBackEvent`'s strength (`GameHooks.onKnockback`) for any player in a game, so zombie hits and any other knockback through `knockback()`. Explosions push separately: a gun's explosion pushes nothing during a match (see `tacz-bridge.md`), whatever this is set to. A vanilla explosion (TNT in a map maker's trap) still pushes as usual. GameTest `KnockbackGameTests`.
- `players.friendly_fire` (false): whether players in a game can hurt each other. Off, `GameHooks.onAttacked` cancels the attack when its source's entity (the player behind a swing, a TacZ bullet, an arrow or a blast) is another player in the same game. A player's own blast still hurts them, and a player outside the game isn't a teammate. Before this setting, players in a game could always hurt each other. GameTest `FriendlyFireGameTests`.
- `powerups.repeat_cooldown_kills` (25): zombie deaths before a power-up that dropped can drop again (see `power-ups.md`). Until 2026-10-23 this was `pickup_cooldown_kills` and counted from the pickup; a map override still naming the old key is ignored.
- `rounds.intermission_ms` (5000; 3000 until 2026-10-23): the break between rounds, in milliseconds, rounded up to whole ticks. Replaces `rounds.intermission_ticks`.
- `export.margin` (6): blocks around the map's zones and machines that `/hs map export` saves when there's no Zone Wand selection (see `map-export-import.md`).
- Per-map, not server config: `/hs map mode edit|play`, saved with the map.
- **CoD scaling** replaced `rounds.count_per_round`, `rounds.player_factor`, and `rounds.speed_growth` with `rounds.ai_per_player` (6), `rounds.run_from_round` (5), `rounds.run_speed` (0.28), and `rounds.spawn_delay_factor` (0.95), and added `powerups.drop_increment` (2000) and `powerups.drop_increment_growth` (1.14). Changed defaults: `base_count` 6 to 24 (it is now CoD's base, not round 1's count), `sprint_from_round` 6 to 9, `health_growth` 0.004 to 0.1, `drop_chance` 0.03 to 0.02. See `game-loop-and-rounds.md` and `power-ups.md`.
- `rounds.runner_chance` (0.1, 2026-10-23): the chance each zombie spawns as a runner, walking at least as fast as a walking player; the last zombie of a round always runs. See `mob-profiles-and-ai-injection.md`.
- **Common config** (`config/hellstorm-common.toml`, 2026-10-23): `zombies.dismemberment` (true). Read by both sides at startup; see `mob-profiles-and-ai-injection.md`.
