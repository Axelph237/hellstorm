# Economy and points

## Goal

Players earn points by hurting and killing zombies and by repairing windows, and spend them on everything in the map. Each player has their own points.

## Data shape

```java
sealed interface PointSource permits Hit, Kill, Repair, Refund, Admin {}
record Hit(boolean headshot) implements PointSource {}
record Kill(boolean headshot, boolean melee) implements PointSource {}
record Repair() implements PointSource {}
record Refund() implements PointSource {}
record Admin() implements PointSource {}
```

`PlayerState.points` is an `int`, never negative.

## Implementation

- `Economy.award(PlayerState, PointSource, GameSettings, boolean doublePoints)` and `Economy.charge(PlayerState, int cost)` are pure. `charge` returns `false` without changing anything if the player can't afford it.
- **Sources:**
  - A non-lethal gun hit awards `Hit`. It comes from TacZ's `EntityHurtByGunEvent.Post`, which fires only when the target survives.
  - A non-lethal melee hit (the player is the direct damage source) awards `Hit`.
  - Every kill goes through Forge's `LivingDeathEvent`. The killer is the damage source's entity. It's melee when the player is also the direct source. The headshot flag comes from `CombatLog`, which keeps the last TacZ `EntityHurtByGunEvent.Pre` for each mob, so a kill is never counted twice.
  - Deaths no player caused (traps, falls) pay nothing.
  - Window repair awards `Repair`, capped per round at `economy.repair_cap_per_round`.
  - As built: repair is also capped per window, at `economy.repair_cap_per_window` per player per round, so a player past it at one window earns again only at another (`Economy.repairPay`, see `windows-and-barricades.md`).
- **Double Points** doubles every award while active.
- **`Purchasable`.** Every purchase in the map goes through one server path: check the player is within reach, check state and power, `charge`, then apply. If apply fails, refund. All purchases are server-authoritative.
- **Sync.** Points changes are batched and sent to clients once per tick for the HUD.

## Config surface

- `economy.hit` (10), `economy.kill` (60), `economy.headshot_kill` (100), `economy.melee_kill` (130), `economy.repair` (10).
- `economy.repair_cap_per_round` (500).
- `economy.repair_cap_per_window` (100).
- `economy.starting_points` (500).

## Assets

- Points HUD (see `hud-and-prompts.md`).

## Depends on

`tacz-bridge.md`, `configuration.md`.

## Verification

- JUnit: each source awards the configured amount, doubled under Double Points.
- JUnit: `charge` refuses when short and never goes negative.
- JUnit: repair awards stop at the per-round cap and resume next round.
- In game: shooting a zombie to death shows hit points, then kill points.

## Phase

2.
