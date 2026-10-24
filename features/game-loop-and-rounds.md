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

`Game` holds the current `GamePhase`, the players, the unlocked room set, power state, active power-ups, and the resolved `GameSettings`.

## Implementation

- **`GameManager`** keeps one `Game` per map level. Nothing is static, so several maps (in several dimensions) can run at once.
- **Tick.** `Game.tick()` switches on the phase and returns the next phase. Transitions:
  - `Lobby → Countdown` on `/hs game start`. The first `/hs game join` in a level runs `MapValidator` and refuses to open a lobby on any ERROR.
  - `Countdown → RoundActive(1)` when the timer ends. Every player is moved to a standable cell in a start room (`StartCells`) in adventure mode. Joining before that doesn't move anyone: they wait (`LifeState.Waiting`, unable to buy or be targeted, with their own inventory), and the end of the countdown saves their state, gives the starting loadout, and puts them in play (see `sessions-and-multiplayer.md`, "As built (waiting for the start)"). Only the largest walkable piece of each start room is used (`StandingArea`), so a box drawn past a wall never gets players.
  - `Intermission(r) → RoundActive(r)` after `rounds.intermission_ms` (milliseconds, default 5000, rounded up to whole ticks).
  - `RoundActive(r) → Intermission(r+1)` when `toSpawn == 0 && alive == 0`.
  - any phase `→ GameOver` when no player is `Alive`.
  - `GameOver → Lobby` after the timer. `MapReset` runs.
- **Round formulas** (`RoundPlan`, pure). Black Ops' own scaling, from its `_zombiemode` script (phase 7; before, the count grew linearly, health barely grew after round 9, and sprinters phased in by chance):
  - Zombie count: `base_count` (24, CoD's `zombie_max_ai`) plus `ai_per_player` (6) for each player past the first, or half of it alone, that extra times the round multiplier: round / 5 (at least 1), and from round 10 also times round x 0.15. Rounds 1 to 5 take 25, 30, 50, 70, and 90% of it. Solo that gives CoD's 6, 8, 13, 18, 24, 27, 28, 28, 29, 33 for rounds 1 to 10, and 60 at round 20.
  - Health: `150 + 100*(r-1)` up to round 9 (950), then times 1 + `health_growth` (0.1, CoD's 10%) each round, all times `health_scale` (0.15) into Minecraft HP, since TacZ damage is in Minecraft HP.
  - Speed: walkers (`walk_speed` 0.23), runners from `run_from_round` (5, `run_speed` 0.28), sprinters from `sprint_from_round` (9, `sprint_speed` 0.32), CoD's move-speed thresholds (round x 8 below 35, below 70, and above). No further growth.
  - Spawn delay: `spawn_delay_ticks` (40, CoD's 2 seconds) times `spawn_delay_factor` (0.95) each round, down to 2 ticks (CoD's 0.08 s).
  - Max alive at once: `rounds.max_alive` (24).
- **Events.** Emits `game_start`, `round_start`, `round_end`, `game_end`, `zombie_spawned`, `zombie_killed` on the game's `GameEvents` bus.
- **Effects go through `GameWorld`.** `Game` is pure: spawning, teleporting, spectating, and status text go through the `GameWorld` interface. `ForgeGameWorld` implements it for a level, and the unit tests use a fake.
- **Commands.** `/hs game join`, `/hs game leave`, and `/hs game status` are open to everyone. `/hs game start` is for players in the game (or ops). `/hs game stop` is op-only. These were `/hs join`, `/hs leave`, `/hs start`, `/hs stop`, and `/hs status` before the command restructure that also made `/hs zones`.
- **Until downs exist (phase 6)**, a player who would die is set to spectator instead, and comes back at the next round start. Until the HUD exists (phase 5), the round, points, and zombies left show on the action bar.
- **Crash safety.** `MapSavedData.gameInProgress` is set at start and cleared at a clean end. If it's still set on server start, the map resets. Games are not resumed.

## Config surface

`rounds.base_count`, `rounds.ai_per_player`, `rounds.health_scale`, `rounds.health_growth`, `rounds.run_from_round`, `rounds.sprint_from_round`, `rounds.walk_speed`, `rounds.run_speed`, `rounds.sprint_speed`, `rounds.max_alive`, `rounds.spawn_delay_ticks`, `rounds.spawn_delay_factor`, `rounds.intermission_ms`, `rounds.countdown_ticks`, `rounds.runner_chance` (see `mob-profiles-and-ai-injection.md`).

## Assets

- Round counter on the HUD (see `hud-and-prompts.md`).

## Depends on

`configuration.md`, `map-model-and-zones.md`, `map-validation.md`, `proximity-spawning.md`.

## Verification

- JUnit on `Game.tick()` with fake time: every transition above fires exactly when its condition holds and not before.
- JUnit on `RoundPlan`: values for rounds 1, 9, 10, 30 match the formulas.
- In game: play rounds 1 to 3 on the two-room map and see the counter advance.

## Phase

2.
