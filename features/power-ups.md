# Power-ups

## Goal

Killed zombies sometimes drop a floating power-up. Any player who touches it triggers it for the whole team.

## Data shape

`data/<ns>/hellstorm/powerups/<id>.json`:

```json
{ "name": "powerup.hellstorm.insta_kill", "weight": 10, "duration_ticks": 600,
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

- `powerups.drop_chance` (0.02), `powerups.max_per_round` (4), `powerups.drop_increment` (2000), `powerups.drop_increment_growth` (1.14), `powerups.repeat_cooldown_kills` (25).
- `powerups.lifetime_ticks` (600), `powerups.blink_ticks` (100).
- Per power-up JSON: weight, duration.

## Assets

- None. The floating pickup and its HUD timer both show the power-up's `item`, chosen per power-up (see Defaults).

## Depends on

`economy-and-points.md`, `tacz-bridge.md`, `windows-and-barricades.md`, `mystery-box.md`.

## Verification

- JUnit: drop roll respects the per-round cap and resets each round.
- GameTest, one per effect: Max Ammo refills, Insta-Kill one-shots, Double Points doubles, Nuke clears mobs, Carpenter boards every window, Fire Sale lowers the box cost.
- GameTest: timed effects expire after their duration.

## Phase

6.

## As built

- **Definitions** (`powerup/PowerUpDef`, loaded by `powerup/PowerUpDefs` from `data/<ns>/hellstorm/powerups/`): `name` (lang key), `weight` (10; 0 never drops at random), `duration_ticks` (600, for timed effects), `item` (the item the pickup and its HUD timer show, default `minecraft:nether_star`; an unknown item rejects the file), `effect: {"type": ...}`. Effects are `powerup/PowerUpEffect`; the timed ones run under a fixed `game/TimedEffects` id (`hellstorm:insta_kill`, `double_points`, `fire_sale`), so two power-ups with the same effect share one timer.
- **Defaults** (`data/hellstorm/hellstorm/powerups/`): Max Ammo (TacZ's diamond ammo box, closed: `tacz:ammo_box{Level:2,AmmoId:"tacz:9mm",AmmoCount:1}`, since TacZ draws the box open while it's empty; weight 10), Insta-Kill (skeleton skull, 10, 30 s), Double Points (gold ingot, 10, 30 s), Nuke (TNT, 6), Carpenter (netherite axe, 6), Fire Sale (emerald, 4, 30 s). The items are the user's picks, 2026-10-29: Max Ammo was a spectral arrow and Carpenter oak planks until then.
- **Drops** (`powerup/PowerUpDrops`, pure): `roll(settings, droppedThisRound, table, random)` returns nothing once `max_per_round` have dropped, else `drop_chance` of a weighted pick. A game takes its table when it is created (`SurvivalContent`, a new `Game` constructor argument; the old constructor uses none). `Game.onMobDied` rolls only for kills a player made, so traps, falls, and a Nuke drop nothing. The count resets at each round start. Drops are queued and put in the world on the next game tick through `GameWorld.dropPowerUp`, which emits `powerup_spawned`.
- **Pickup** (`powerup/PowerUpEntity`, registered in `powerup/PowerUpRegistries` as `hellstorm:power_up`): floats at eye level over where the zombie died (see below), counts down `powerups.lifetime_ticks`, blinks for its last `blink_ticks`, and removes itself when the level has no game. Each tick it checks the game's own players (not the level's entity list), so any non-spectator player of the game touching it collects it through `Game.collectPowerUp`, which emits `powerup_collected` and refuses players who aren't alive or a game that's over. `MapReset` removes every pickup in the level.
- **Rendering** (`client/PowerUpRenderer`): the def's item, full bright, bobbing and spinning, skipped on the blink's dark beats. No textures.
- **Effects** (`powerup/PowerUps.apply`), each for the whole team, with the name shown to every player through `Notices` and a beacon sound:
  - Max Ammo: `TaczBridge.refillAmmo` on every inventory and offhand gun of every player.
  - Insta-Kill: while running, a `LivingHurtEvent` (high priority) sets any damage a player of the game does to one of its zombies to five times the zombie's remaining health, which survives armor. Guns and melee alike, and the kill still pays as usual.
  - Double Points: `Game` passes `timedActive(DOUBLE_POINTS)` to `Economy.award` for hits, kills, and repairs. Bonuses aren't doubled.
  - Nuke: `kill()`s every game zombie (no killer, so no points and no drops), then `Game.awardEveryone(powerups.nuke_bonus)`.
  - Carpenter: every window of the map to its max boards, then `awardEveryone(powerups.carpenter_bonus)`.
  - Fire Sale: `MysteryBoxes.setFireSale(true)`; `GameWorld.timedEffectEnded` turns it off when the timer runs out or the game ends.
- **HUD.** Timed effects show in the foundation's timer row, each the power-up's item and then "Insta-Kill 23". `Game.startTimed` takes the item with the name, `GameWorld.Timer` carries it, and `HudSyncPacket` sends it in its `/give` form (2026-10-29; text only before).
- **Tests.** JUnit: `PowerUpDropsTest` (the cap, the chance and weights over many kills, zero weight), `PowerUpDefTest` (the six defaults, field defaults, bad files), `game/PowerUpGameTest` (player kills drop up to the cap and it resets next round, kills nobody made don't drop, Double Points doubles hits and kills and stops with its timer, collecting needs an alive player and a running game). GameTests (`PowerUpGameTests`): Max Ammo refills a held and a stowed gun; Double Points doubles a repair and shows its timer; Carpenter boards two windows and pays the bonus; Fire Sale opens both box locations at the Fire Sale price and closes all but the home when its 20-tick timer ends; Insta-Kill makes a 1-damage punch kill a real game zombie and pay a melee kill; Nuke kills every game zombie as kills nobody made and pays only the bonus; walking into a dropped pickup collects it once and removes it.
- **Eye level** (2026-10-22). A drop finds the floor under where the zombie died (the top of the first block with a collision shape, looking up to 4 blocks down, so a zombie killed mid-jump or on a slab still works) and floats its item 1.5 above it (`PowerUps.FLOAT_HEIGHT`), in the second block up, about a standing player's eye height (1.62). The entity sits `PowerUpEntity.ITEM_LIFT` (0.15) below that, since the renderer draws the item that far up before its bob. A player standing under it touches it. GameTest `PowerUpGameTests.walkingIntoAPickupCollectsItOnce` checks the height over the floor and mid-jump, and a player standing on the floor collecting it.
- **Repeat cooldown** (2026-10-23, `powerups.repeat_cooldown_kills`, 25; 0 turns it off). A power-up that drops, picked up or not, sits out of the drop table (`PowerUpDrops.Table.without`) for the next 25 game zombie deaths, whoever or whatever killed them, so the 26th can drop it again. Each power-up has its own; others can drop meanwhile. The special round's guaranteed last-kill drop ignores the cooldowns but starts one. Cooldowns last the game. It was a pickup cooldown first (2026-10-22): a power-up left on the ground could drop again at once, and one round dropped two pairs of the same power-up. JUnit `PowerUpGameTest.aDroppedPowerUpSitsOutTheNextKillsBeforeItCanDropAgainPickedUpOrNot`.
- **Not built:** per-power-up sounds.

## CoD drop scaling (phase 7)

Drops follow Black Ops: a 2% chance on any player's kill (`drop_chance`), plus points-triggered drops. The game keeps the team's earned points (hits, kills, repairs, and Nuke and Carpenter bonuses; not refunds). Once they reach the next threshold, the next player kill drops a power-up even without the chance, the threshold moves `drop_increment` further on from the current total, and the increment grows by `drop_increment_growth`: 2000 points, then 2280 more, 2599 more, and so on. `max_per_round` still caps both kinds. `PowerUpDrops.roll(settings, droppedThisRound, pointsDue, table, random)` decides; `Game` tracks the points. Tests: `PowerUpDropsTest.pointsReachingTheThresholdDropEvenWithNoChanceButNotPastTheCap` and `PowerUpGameTest.theTeamsPointsTriggerDropsAtAGrowingThresholdWithoutAnyChance`.
- **Sounds** (2026-10-23, `game/GameSounds`). A drop plays the elder guardian's curse at pitch 1.2 where it lands; a pickup plays the ender dragon's fourth growl and the ravager's celebrate, pitched down, to everyone in the game, in place of the beacon. The drop's stands in for 1.20.5's bad omen. See `NEEDED/SOUNDS.md`.
