# Game loop and rounds

## Goal

A game runs as an explicit state machine on the server: players join, rounds start and end, zombies get harder each round, and the game ends when everyone is down.

## Data shape

```java
sealed interface GamePhase permits Lobby, Countdown, Intermission, RoundActive, GameOver {}
record Lobby() implements GamePhase {}
record Countdown(int ticksLeft) implements GamePhase {}
record Intermission(int round, int ticksLeft) implements GamePhase {}
record RoundActive(int round, int toSpawn, int alive, int spawnCooldown) implements GamePhase {}
record GameOver(int roundReached, int ticksLeft) implements GamePhase {}

sealed interface LifeState permits Alive, Downed, Spectating {}
record Alive() implements LifeState {}
record Downed(int bleedTicks, Optional<UUID> reviver, int reviveProgress) implements LifeState {}
record Spectating(boolean respawnNextRound) implements LifeState {}

final class PlayerState { UUID id; LifeState life; int points; Set<ResourceLocation> perks; Stats stats; }
```

`Game` holds the current `GamePhase`, the players, the unlocked zone set, power state, active power-ups, and the resolved `GameSettings`.

## Implementation

- **`GameManager`** keeps one `Game` per map level. Nothing is static, so several maps (in several dimensions) can run at once.
- **Tick.** `Game.tick()` switches on the phase and returns the next phase. Transitions:
  - `Lobby → Countdown` on `/pap start`. The first `/pap join` in a level runs `MapValidator` and refuses to open a lobby on any ERROR.
  - `Countdown → RoundActive(1)` when the timer ends. Every player is moved to a standable cell in a start zone (`StartCells`) in adventure mode. Joining before that doesn't move anyone. Only the largest walkable piece of each start zone is used (`StandingArea`), so a box drawn past a wall never gets players.
  - `Intermission(r) → RoundActive(r)` after `rounds.intermission_ms` (milliseconds, default 3000, rounded up to whole ticks).
  - `RoundActive(r) → Intermission(r+1)` when `toSpawn == 0 && alive == 0`.
  - any phase `→ GameOver` when no player is `Alive`.
  - `GameOver → Lobby` after the timer. `MapReset` runs.
- **Round formulas** (`RoundPlan`, pure):
  - Zombie count: `rounds.base_count + rounds.count_per_round * r`, times a player-count factor.
  - Health: CoD-style `150 + 100*(r-1)` up to round 9, then growing `rounds.health_growth` (0.4%, where CoD uses 10%) per round, times `rounds.health_scale` (0.15). Speed grows `rounds.speed_growth` (0.01%) per round (TacZ damage is in Minecraft HP).
  - Speed: walkers until `rounds.sprint_from_round`, mixed after.
  - Max alive at once: `rounds.max_alive` (default 24).
- **Events.** Emits `game_start`, `round_start`, `round_end`, `game_end`, `zombie_spawned`, `zombie_killed` on the game's `GameEvents` bus.
- **Effects go through `GameWorld`.** `Game` is pure: spawning, teleporting, spectating, and status text go through the `GameWorld` interface. `ForgeGameWorld` implements it for a level, and the unit tests use a fake.
- **Commands.** `/pap join`, `/pap leave`, and `/pap status` are open to everyone. `/pap start` is for players in the game (or ops). `/pap stop` is op-only.
- **Until downs exist (phase 6)**, a player who would die is set to spectator instead, and comes back at the next round start. Until the HUD exists (phase 5), the round, points, and zombies left show on the action bar.
- **Crash safety.** `MapSavedData.gameInProgress` is set at start and cleared at a clean end. If it's still set on server start, the map resets. Games are not resumed.

## Config surface

`rounds.base_count`, `rounds.count_per_round`, `rounds.player_factor`, `rounds.health_scale`, `rounds.health_growth`, `rounds.speed_growth`, `rounds.sprint_from_round`, `rounds.max_alive`, `rounds.spawn_delay_ticks`, `rounds.intermission_ms`, `rounds.countdown_ticks`.

## Assets

- Round counter on the HUD (see `hud-and-prompts.md`).

## Depends on

`configuration.md`, `map-model-and-zones.md`, `map-validation.md`, `proximity-spawning.md`.

## Verification

- JUnit on `Game.tick()` with fake time: every transition above fires exactly when its condition holds and not before.
- JUnit on `RoundPlan`: values for rounds 1, 9, 10, 30 match the formulas.
- In game: play rounds 1 to 3 on the two-zone map and see the counter advance.

## Phase

2.
