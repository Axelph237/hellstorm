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

## As built (phase 4)

- **Profile JSON:** only `entity` is required. The optional fields are:
  - `weight` (10), `min_round` (1), `max_round`
  - `ai` (`replace` | `augment`)
  - `nbt` (an SNBT string)
  - `health_multiplier`, `speed_multiplier`
  - `damage` (4)
  - `breaks_barricades` (true)
- **Typos are errors.** All optional fields use `Codecs.strictOptional`, so a typo like `"ai": "agument"` fails to load instead of silently becoming the default. In 1.20.1, `optionalFieldOf` would do the latter. `CodecLintTest` stops `optionalFieldOf` from coming back.
- **Shipped default.** The mod ships `packapunch:zombie`. If no datapack defines any profile, the same zombie is built in.
- **Goals:**
  - `TargetGamePlayerGoal` only considers the game's own alive players. Mobs never scan every player in the level.
  - `GameMeleeGoal` deals the profile's damage directly. Many non-hostile and modded mobs lack the attack-damage attribute that vanilla's melee goal reads.
- **Support tiers:** decided from a live instance (`SupportTier`). Brain-driven mobs (any memories registered) are forced to `augment`. Tier warnings are logged once per profile per game.
- **`/pap mobtest <profile>`** spawns the profile where you're looking, with the game AI and you as its target, rather than building a test pen. It reports the support tier, the AI mode actually used, the hitbox, the movement class, and any windows on the map the mob is too big for.
- **Validation** warns when a profile's hitbox (from its entity type) can't fit a window it may have to climb through.
