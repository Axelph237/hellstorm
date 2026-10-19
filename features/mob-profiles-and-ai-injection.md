# Mob profiles and AI injection

## Goal

Any mob, vanilla or from another mod, can be a zombie. A datapack names the entity and how it should behave, and the mod injects zombies AI into it at spawn.

## Data shape

`data/<ns>/packapunch/mob_profiles/<id>.json`:

```json
{
  "entity": "minecraft:zombie",
  "weight": 10,
  "rounds": { "min": 1, "max": null },
  "ai": "replace",
  "nbt": {},
  "health": "cod",
  "speed": { "base": 0.23, "sprint_from_round": 6, "sprint": 0.32 },
  "damage": 4.0,
  "tags": ["breaks_barricades"]
}
```

- `ai`: `replace` clears the mob's own goals, `augment` keeps them and adds ours.
- `health`: `cod` uses the round curve from `game-loop-and-rounds.md`, or a number for fixed health.

`GameMob` capability on every spawned mob: `{ gameId, profileId, assignedWindow, targetZone }`.

## Implementation

**Spawn.** `SpawnDirector` calls `EntityType.create`, applies the profile NBT, positions it, calls `finalizeSpawn`, attaches `GameMob`, then calls `AiInjector.inject(mob, profile, game)`.

**`AiInjector`:**
- In `replace` mode, `goalSelector.removeAllGoals(g -> true)` and the same for `targetSelector`. If `goalSelector` isn't accessible in 1.20.1 Mojmap, use an access transformer (phase 0 spike).
- Adds `BreakBarricadeGoal`, `PortalNavigateGoal` (see `zombie-pathfinding.md`), `TargetGamePlayerGoal` (only `Alive` players in the same game), and a `MeleeAttackGoal` if the mob has none.
- Applies `AttributeModifier`s for health, speed, attack damage, and a larger follow range.
- Calls `setPersistenceRequired()`.

**Suppression,** only for mobs with `GameMob` (unlike ZombieRool, which retargets every mob in the world):
- `LivingDropsEvent` and `LivingExperienceDropEvent` cancelled.
- Fire cleared each tick and fire damage cancelled, so sun-burning mobs survive daylight.
- Natural spawns in a map level are cancelled with `MobSpawnEvent.FinalizeSpawn#setSpawnCancelled(true)`.

**Support tiers,** checked when profiles load:
- **Works:** any `PathfinderMob` driven by goals. That covers most vanilla hostiles and most modded mobs, including GeckoLib ones.
- **Partial:** Brain-based mobs (piglin, hoglin, warden, villager). Their Brain fights our goals for movement. Only `augment` is allowed, with a WARNING.
- **Unsupported for navigation:** non-`PathfinderMob` mobs (ghast, phantom) and mods that rebuild goals every tick. WARNING. They still count as zombies and can target players.

**Testing aid.** `/pap mobtest <profile>` builds a small test pen with a window and two zones and spawns the profile there.

## Config surface

All per profile. Global: `mobs.follow_range` (default 64).

## Assets

None. Mobs use their own models.

## Depends on

`game-loop-and-rounds.md`, `proximity-spawning.md`, `windows-and-barricades.md`, `zombie-pathfinding.md`.

## Verification

- `/pap mobtest` with a zombie, a skeleton, and one modded mob: each breaks the window and paths across two zones.
- Profile loading: a piglin profile with `replace` logs a WARNING and is forced to `augment`.
- A wild zombie outside the game is not affected (no goals added, normal drops).
- Killed game mobs drop nothing.

## Phase

4.
