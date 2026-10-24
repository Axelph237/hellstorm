# Mob profiles and AI injection

## Goal

Any mob, vanilla or from another mod, can be a zombie. A datapack names the entity and how it should behave, and the mod injects zombies AI into it at spawn.

## Data shape

`data/<ns>/hellstorm/mob_profiles/<id>.json`:

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

**Testing aid.** `/hs mobtest <profile>` builds a small test pen with a window and two zones and spawns the profile there.

## Config surface

All per profile. Global: `mobs.follow_range` (default 64).

## Assets

None. Mobs use their own models.

## Depends on

`game-loop-and-rounds.md`, `proximity-spawning.md`, `windows-and-barricades.md`, `zombie-pathfinding.md`.

## Verification

- `/hs mobtest` with a zombie, a skeleton, and one modded mob: each breaks the window and paths across two zones.
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
- **Shipped default.** The mod ships `hellstorm:zombie`. If no datapack defines any profile, the same zombie is built in.
- **Goals:**
  - `TargetGamePlayerGoal` only considers the game's own alive players. Mobs never scan every player in the level.
  - `GameMeleeGoal` deals the profile's damage directly. Many non-hostile and modded mobs lack the attack-damage attribute that vanilla's melee goal reads.
- **Support tiers:** decided from a live instance (`SupportTier`). Brain-driven mobs (any memories registered) are forced to `augment`. Tier warnings are logged once per profile per game.
- **`/hs mobtest <profile>`** spawns the profile where you're looking, with the game AI and you as its target, rather than building a test pen. It reports the support tier, the AI mode actually used, the hitbox, the movement class, and any windows on the map the mob is too big for.
- **Validation** warns when a profile's hitbox (from its entity type) can't fit a window it may have to climb through.

- **Door opening.** Ground-navigating game mobs can open wooden doors (`setCanOpenDoors` plus `OpenDoorGoal`, which leaves them open), so a door's state when a game starts doesn't cut zones apart.
- **Window-aware spawning.** The spawner only picks a profile that fits the marker's window. It uses `GameWorld.canSpawn`, which compares the profile's entity-type size, rounded up to whole blocks, with the window opening. An iron golem (2x3) is never sent to a 1x2 or 2x2 window.
- **Health and speed per profile.** A game mob gets the round's zombie health (`RoundPlan.zombieHealth`) times its profile's `health_multiplier`, and moves `speed_multiplier` times as fast on the ground as the round's zombies (both default 1; see "Speed" below). The shipped `hellstorm:hellhound` has `health_multiplier` 0.0242424: 2 HP in round 5, the first hounds round, against a zombie's 82.5, so a hound is nearly one shot; 3.8 HP in round 10. Until 2026-10-23 it was 0.33. Vanilla caps max health at 1024, which zombies reach at round 30 and hounds only at round 69. GameTest `GameLoopGameTests.aHoundInTheFirstHoundsRoundHasTwoHealth`.
- **Navigation per profile.** `"navigation": "flow"` (the default) walks on vanilla navigation with the game's collision checks, and a mob far from its target is guided down the game's distance field (see `zombie-pathfinding.md`). `"navigation": "vanilla"` keeps the mob's own pathfinding and re-paths to its target every 10 ticks. Mobs whose navigation isn't walk-based (spiders on walls, fliers, swimmers) always use their own, whatever the profile says. Game mobs get a follow range of 128 so vanilla navigation finds players across a map.
- **Vanilla navigation's search budget.** Vanilla pathfinding visits at most 16 nodes per block of follow range. It sizes that budget when the mob is constructed, from its type's default range: 560 nodes for a zombie and 256 for a wolf. Raising the attribute later doesn't change it. `AiInjector` scales the budget to the 128-block range (2048 nodes) with `setMaxVisitedNodesMultiplier`. Without that, a mob on vanilla navigation (a `vanilla` profile, or a `flow` mob before its target's distance field is ready) stopped at the wall nearest a far player when the route went the long way round. GameTests `aVanillaNavigatingZombieWalksOutOfADeadEndToAFarPlayer` and `aVanillaNavigatingWolfWalksOutOfADeadEndToAFarPlayer` cover this. Targeting has no range or sight limit. `aGameZombieChasesAFarPlayerItCannotSee` and `aGameHoundChasesAFarPlayerItCannotSee` check that a game mob 46 blocks away, behind a wall, targets the player at once and reaches them. `aZombieTurnsToTheFarPlayerWhenTheNearOneStopsBeingATarget` checks that a zombie switches targets within half a second.

## Dismemberment and crawlers (2026-10-23)

- **Where a shot lands** (`mob/BodyPart`). TacZ tells a headshot from where its bullet struck; its hurt events carry only the flag, so `tacz/mixin/EntityKineticBulletHitMixin` notes the hit point as TacZ's `onHitEntity` starts (`tacz/BulletHits`, keyed by bullet), and the survived-hit handler reads it back. `BodyPart.at` places the point on the zombie's model, 32 pixels tall: legs to pixel 12, body and arms to 24, the head above (or wherever TacZ called a headshot). Across, the hitbox (0.6 wide, narrower than the model) is scaled to the model's 16 pixels: the middle 8 are the body, the 4 each side an arm; a leg is the left or right half. Left and right are the zombie's own, from its body's yaw. JUnit `BodyPartTest`.
- **Limbs come off** (`mob/Dismemberment`). A non-lethal TacZ hit on a game `minecraft:zombie`'s arm or leg adds its damage to that limb; once a limb has taken 55% of the zombie's max health (`LIMB_SHARE`, 0.55: about three pistol shots in round 1, more later; first 0.2, then 0.4), it comes off, with a spray of dark red dust (blood) and a squish. The head and body never come off. A crawler loses nothing more.
- **A lost leg makes a crawler** that moves 0.8 times as fast (`CRAWL_SPEED`, 0.8; 0.5 until 2026-10-23, and until 2026-10-24 the 0.8 went on the speed attribute, so crawlers moved at 0.64).
- **The crawler tag** (`mob/Limbs`). `ForgeData` holds `hellstorm_limbs` (a byte of lost limbs) and `hellstorm_crawler: {speed_multiplier: <f>}`. The tag does it all: on both sides `EntityEvent.Size` lays the hitbox prone (0.6 by 0.6); on the server, when a tagged zombie joins the level or gets the tag, a `MULTIPLY_TOTAL` speed modifier of `sqrt(speed_multiplier) - 1`, so it moves `speed_multiplier` times as fast. A missing speed, or one not above 0, is `CRAWL_SPEED` (`Limbs.crawlSpeed`); until 2026-10-24 it read as 0, which froze the zombie and had clients draw it standing. Both are saved with the zombie. `LimbsPacket` tells players the limbs and the crawl when one changes or they start seeing the zombie.
- **The crawler profile** `hellstorm:crawler`: `minecraft:zombie` with `{ForgeData:{hellstorm_crawler:{speed_multiplier:0.8f}}}`, weight 0, so only wave events or scripts pick it (as `hellhound` is).
- **Drawing them** (`client/ZombieLimbs`). Hellstorm replaces vanilla's zombie renderer with one that matches it, but hides lost limbs, and lays a crawler face down along its heading (as vanilla lays a crawling player: -90 degrees about its side, then 1 block back and 0.3 up), its arms reaching out past its head in turn as it moves and its legs dragging. Armor drawn on a lost limb still shows. Another mod that replaces the zombie renderer would undo this, or this would undo it.
- **Off switch** (`config/hellstorm-common.toml`, `[zombies] dismemberment`, true; `config/CommonConfig`). Off, nothing comes off, and the client registers no zombie renderer at all, so Minecraft's, or another mod's that replaces it, draws zombies. (Registering Minecraft's own renderer instead would override another mod's.) Both sides read it at startup, so it takes a restart, and each client reads its own copy. Forge registers renderers before it loads the common config, so `ZombieLimbs` reads `hellstorm-common.toml` itself at registration (the default if it's missing or unreadable) and logs what it did ("Zombies drawn by ..."). Checked in a Prism instance with ETF, EMF and Fresh Animations, with it on and off. A zombie tagged as a crawler still has the prone hitbox and slower speed with it off, but is drawn standing.
- **With ETF, EMF, and Fresh Animations** (checked in game, 2026-10-23): textures and Fresh Animations' model work; lost limbs stay hidden and crawlers lie prone, but Fresh Animations' own animation replaces the crawl's arm reach.
- **Tests.** GameTests `DismembermentGameTests`: nothing comes off with the switch off; half a limb's share takes nothing, the rest takes the right arm, an armless zombie still walks, the body and head never come off, a shot leg makes a crawler with the prone hitbox and the square root of 0.8 times the speed attribute, a crawler loses nothing more, and the lost limbs are saved; the crawler profile crawls from the start with the square root of 0.8 times a walker's speed attribute; a crawler tag with no speed crawls at 0.8 and clients are told it crawls; and a fake player's real TacZ AK, aimed at a zombie's legs, takes a leg off and makes it crawl. How it looks is checked in game.

## Size (2026-10-23)

- **`scale` in a profile** (0.1 to 10, default 1) sizes its mob: hitbox, eye height, and how it's drawn. `hellstorm:hellhound` has 1.5, so a hound is 0.9 by 1.275 blocks, half again a wolf, and still moves as 1x2, so it fits a standard window. `MobFactory` sets it, so game spawns and `/hs mobtest` both get it.
- **The tag** (`mob/MobScale`). `ForgeData` holds `hellstorm_scale` (a float; anything not above 0 reads as 1), so a profile's `nbt` can set it too, and it saves with the mob. On both sides one `EntityEvent.Size` listener lays a crawler's hitbox prone and then multiplies the hitbox and the eye height by the scale; it replaced `Limbs`' own size listener, so the order is fixed. `ScalePacket` tells a client when it starts tracking a scaled mob.
- **Drawing** (`client/MobScales`). No mixins: Forge's `RenderLivingEvent.Pre` pushes the pose and scales it about the mob's feet, at the lowest priority so a cancelled render never pushes, and `Post` pops it, since the caller draws the shadow and the debug hitbox after. GeckoLib mobs don't fire Forge's event, so `GeoRenderEvent.Entity.Pre` scales them; GeckoLib pushes before it and pops after its Post, so nothing is popped there. The shadow keeps its size, and a GeckoLib mob's name tag stays at its normal height. Step height and speed don't scale.
- **Windows.** `MobProfile.hitbox` is the type's size times the scale, and both `GameWorld.canSpawn` and map validation use it, so a scaled-up profile can be too big for a window its entity type would fit.
- **Tests.** GameTests `MobSizeAndSpeedGameTests`: a hellhound's hitbox and eye height are 1.5 times a wolf's and it loads from a save at that size, while a plain wolf keeps its own; a zombie at scale 2 crawls with a prone hitbox twice the size. `WindowGameTests.theDirectorOnlySpawnsMobsThatFitTheMarkersWindow` refuses a zombie scaled to 1.5 at a 1x2 window. How they look is checked in game.

## Runners (2026-10-23)

- **A runner walks as fast as a walking player** (`mob/Runners`, `SPEED`, 0.316; 1.2 times its own speed until 2026-10-23). A mob's ground speed goes with the square of its speed attribute: vanilla's `Mob.setSpeed` uses the speed both as the forward input and as the acceleration, and Hellstorm always navigates at a speed modifier of 1. Measured on a flat track, ground speed is 43.17 x attribute squared: 0.23 (round 1) walks 2.28 blocks a second, 0.28 (from round 5) 3.38, 0.32 (from round 9) 4.42. A player walks 4.317 (0.1 x 0.98 / (1 - 0.546) x 20), which a mob reaches at 0.316 (4.31). Under 1.2 a runner walked 3.29, 4.87, and 6.37 in those rounds, faster than a sprinting player (5.6) from round 9.
- **A target, not a multiplier.** The runner's speed is raised to 0.316 by an `ADDITION` modifier of the difference from its base (the round's speed, scaled by the profile's `speed_multiplier`). A mob already that fast, a sprinting zombie or a hellhound (2.25 times as fast), keeps its own speed: running never slows anything. A multiplier can't give one target speed across the rounds' 0.23 to 0.32, and a profile that should be fast stays fast. The crawler's `MULTIPLY_TOTAL` still applies on top, so a running crawler moves 0.8 times as fast as a runner (about 3.45 blocks a second). It isn't a setting, as 1.2 wasn't; `rounds.runner_chance` is. Marked by `hellstorm_runner: 1b` in `ForgeData`, which a profile's `nbt` can set.
- **Who runs.** Each spawn runs with `rounds.runner_chance` (0.1), rolled by `Game` and applied through `GameWorld.makeRunner`. Once a round has nothing left to spawn and one zombie alive, that zombie runs, if it doesn't already (`Game.tickRound`). The roll uses the game's random only when the chance is above 0, so games with it at 0 pick the same spawns as before.
- **Tests.** Unit tests in `GameTest`: with the chance at 1 every spawn runs; with it at 0 two zombies left walk and the last one is made to run once; a lone zombie with more still to spawn walks. GameTest `aRunnerIsRaisedToTheRunSpeedOnceAndACrawlerRunsAtEightTenthsOfIt`: a runner made twice has 0.316, a running crawler the square root of 0.8 times that. GameTest `aRunnerWalksAsFastAsAWalkingPlayer` drives three zombies down a 64-block track with their move control and measures blocks a second: a round 1 runner within 5% of a walking player, a round 1 walker slower, and a sprint-round runner keeping its own 0.32. GameTest `aCrawlerMovesEightTenthsAsFastAsAWalker` measures a round 1 crawler against a walker on the same track: within 5% of 0.8 (measured 2026-10-24: walker 2.28, crawler 1.83 blocks a second, 0.800).

## Drops (2026-10-23)

- **A game mob drops nothing** (`game/GameHooks`), whatever mod it comes from: `LivingDropsEvent` (loot, equipment, and anything a mod adds to the list) and `LivingExperienceDropEvent` are cancelled for any mob with the `hellstorm_game` tag. Both listeners run at `HIGHEST`, so another mod's drop listener never runs for a game mob. This much was already there at `NORMAL` priority.
- **What a dying game mob spawns** (`onSpawnedByDyingMob`). While a game runs in the level, an entity joining fresh within a block of a game mob that is dead or dying is taken to come from it. An item (unless a player threw it) or an XP orb is cancelled, which catches a mob that drops something itself rather than through the events. A mob, such as a slime's young or a mod's mob that leaves a head or ghost behind, gets the `hellstorm_game` tag, so it drops nothing in turn, doesn't burn, and is removed if it loads from disk later. It also gets `hellstorm_spawned_by_game_mob`: the game doesn't track it, so it doesn't count toward the round, and it's discarded on its next tick once its level has no game or the game is over. Power-ups are their own entity, so they still drop. Before this, a game slime's young were plain slimes that dropped slimeballs and XP and outlived the game.
- **Tests.** GameTest `MobDropsGameTests.gameMobsAndTheYoungTheyLeaveDropNothing`: a player kills a game zombie with a sword and a helmet at drop chance 2, and a game slime of size 2; the slime's young are killed too; no item or XP orb appears, and the young of a second slime are gone once the game ends. With `onSpawnedByDyingMob` turned off, the young dropped 4 slimeballs and 3 XP orbs.

## Speed means ground speed (2026-10-24)

- **`speed_multiplier` is how many times as fast** a mob moves on the ground, in a profile and in the crawler tag. Ground speed goes with the square of the speed attribute (see Runners above), so `mob/GroundSpeed.attributeFactor` takes the square root before it goes on the attribute: `ForgeGameWorld.spawnZombie` sets the round's speed times it, `/hs mobtest` 0.23 times it, and the crawl's `MULTIPLY_TOTAL` modifier is it minus 1. Until 2026-10-24 the multiplier went on the attribute as it was, so 1.5 moved a mob 2.25 times as fast and the crawl's 0.8 moved it at 0.64.
- **The hellhound** has 2.25, up from 1.5, so it moves as fast as it did. The crawler profile and `Dismemberment.CRAWL_SPEED` keep 0.8, which now means what the docs said: crawlers are faster than before (0.8 of a walker, up from 0.64). The runner's target, 0.316, is an attribute value and didn't change.
- **The example datapacks** keep their numbers, which now move their mobs that many times as fast: `zombies_more_test`'s tank 0.75 and spider crawler 1.2 (its README already says "times the speed"), and `hellstorm_example`'s iron golem 0.8.

## Strict loading (2026-10-24)

- **A datapack file with any error is skipped whole** (`util/DataFiles.parse`), with a log line naming the file and the error: mob profiles, power-ups, wave events, crate pools, gun balance and script event defs. Before, they loaded with `resultOrPartial`, which kept an out-of-range value with only a log line: `"weight": -1` loaded, `speed_multiplier: 0` made zombies that never moved. A wrong type already skipped the file. Upgrade files skip a bad entry alone, as before. World saves (`MapSavedData`, `ConfigBlockEntity.load`) still load what they can. `CodecLintTest.partialResultsAreOnlyKeptFromWorldSaves` keeps `resultOrPartial` out of everything else.
- **Skipped files are kept** for the last load of each kind, in `DataFiles.errors()`: kind ("mob profile", "power-up", "wave event", "crate pool", "gun balance", "event def") to a list of `"<file id>: <why>"`. Nothing shows them in game yet.
- **A profile's `nbt`** must parse as SNBT, and a crawler tag's `speed_multiplier`, if it has one, must be a number above 0; otherwise the profile is skipped. Before, bad SNBT only failed when a mob spawned.
- **Tests.** Unit tests `DataFilesTest` (an out-of-range value and a wrong type each skip their file and keep its error, a loader's own check skips too, a clean reload clears the errors, mob profiles with speed 0 or weight -1 are skipped) and `MobProfileTest.nbtThatDoesntParseOrStopsACrawlerIsRefused`.
