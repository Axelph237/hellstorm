# Power-ups

## Goal

Killed zombies sometimes drop a floating power-up. Any player who touches it triggers it for the whole team.

## Data shape

`data/<ns>/packapunch/powerups/<id>.json`:

```json
{ "name": "powerup.packapunch.insta_kill", "weight": 10, "duration_ticks": 600,
  "effect": { "type": "insta_kill" } }
```

Effect types (codec dispatch): `max_ammo`, `insta_kill`, `double_points`, `nuke`, `carpenter`, `fire_sale`.

`PowerUpEntity`: `{ powerUpId, ticksLeft }`.
`Game.activePowerUps`: `Map<ResourceLocation, Integer>` of remaining ticks for timed effects.

## Implementation

- **Drop roll.** On a game mob kill, if this round's drops are below `powerups.max_per_round`, roll `powerups.drop_chance`. On success, pick by weight and spawn a `PowerUpEntity` at the mob's position. Emit `powerup_spawned`.
- **Entity.** Floats and spins, blinks in its last `powerups.blink_ticks`, and despawns after `powerups.lifetime_ticks`.
- **Pickup.** A player in the same game touching it triggers the effect and emits `powerup_collected`.
  - `max_ammo`: `TaczBridge.refillAmmo` on every gun of every player.
  - `insta_kill`: timed. The damage hook sets damage to the target's health.
  - `double_points`: timed. `Economy` doubles awards.
  - `nuke`: kills every game mob, awards a flat bonus, and the kills award no points.
  - `carpenter`: sets every window to full boards and awards a bonus.
  - `fire_sale`: timed. Box cost drops, and every box location is active.
- **Wave events** can force a drop (for example Max Ammo on the last kill of a special round).

## Config surface

- `powerups.drop_chance` (0.03), `powerups.max_per_round` (4).
- `powerups.lifetime_ticks` (600), `powerups.blink_ticks` (100).
- Per power-up JSON: weight, duration.

## Assets

- **Needed from you:** one sprite per default power-up for the floating entity, and one HUD icon each for the timed ones. Listed in `TEXTURES_NEEDED.md`. Until then the entity renders a vanilla item (for example `minecraft:nether_star`).

## Depends on

`economy-and-points.md`, `tacz-bridge.md`, `windows-and-barricades.md`, `mystery-box.md`.

## Verification

- JUnit: drop roll respects the per-round cap and resets each round.
- GameTest, one per effect: Max Ammo refills, Insta-Kill one-shots, Double Points doubles, Nuke clears mobs, Carpenter boards every window, Fire Sale lowers the box cost.
- GameTest: timed effects expire after their duration.

## Phase

6.
