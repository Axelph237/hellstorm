# Pack-a-Punch: a CoD Zombies mode for Minecraft on TacZ

## Context

The goal is a Forge mod that turns Minecraft worlds into Call of Duty: Zombies maps. That covers round-based waves, points, doors, wall-buys, a Mystery Box, perk machines, Pack-a-Punch, downs and revives, power-ups, a power switch, traps, and wave events. In the MVP, maps are built by hand with map-maker tools. The tools' zone visuals are modeled on Create's selection outlines. Procedural map generation comes after the MVP, but the map model is designed now so the generator can be added without changing anything else. The mod depends on TacZ for guns. Co-op on dedicated servers is a v1 requirement. Any mob, including mobs from other mods, can be configured as a "zombie".

The project directory `pack-a-punch` is empty, so this is a greenfield build.

**Target.** Minecraft 1.20.1, Forge 47.x, Java 17, TacZ `1.1.8-hotfix2`. That is the newest official TacZ (published 2026-10-02), and it only ships for Forge 1.20.1 (checked against the Modrinth version API). The NeoForge 1.21.1 build is an unofficial port and is out of scope.

**NeoForge 1.20.1 compatibility.** NeoForge's 1.20.1 line is a fork of Forge 47.1 that loads Forge 1.20.1 mods. It stopped at `47.1.106` (maven last updated May 2024), while Forge 1.20.1 is still maintained. So the mod builds against Forge and keeps one jar loading on both:
- **Forge 47.1 APIs only.** Don't use APIs added after Forge 47.1. Example: Forge 47.4 deprecates the static `ModLoadingContext.get()`, but its replacement (constructor injection) doesn't exist in NeoForge 1.20.1, so the mod keeps the static getters.
- **Boot check.** The release jar is boot-tested on NeoForge `1.20.1-47.1.106` with TacZ and Create. First passed on 2026-10-19.

**Prior art.** ZombieRool (https://github.com/Cryo60/ZombieRool) is a Forge 1.20.1 mod with optional TaCZ support. We read its source and decided to build our own mod instead of an add-on or a contribution. The reasons:
- It can't be built from the repo, it has a single author, nobody has ever opened a pull request, and there is no LICENSE file.
- It has no zone graph. Doors, spawners and traps are linked only by integer channels.
- It has no proximity spawning and no room detection.
- Its zombie types are a fixed enum, so mobs from other mods can't be used.
- Every game value is a global static, so only one game can run per server.
- Its Lua scripting has no sandbox, which matters because maps can be downloaded.

These are the parts this plan builds as its foundation. Two of its ideas are worth adapting. Barricade collision drops away when the planks reach 0. Weapon JSON maps each gun to a TacZ gun id and a separate Pack-a-Punch gun id. One of its mistakes to avoid: a mixin forces every `Mob` in the world to target players. Our AI injection only touches mobs that our game spawned and tagged.

## Project docs: `PLAN.md` and `features/`

`PLAN.md` (this file) is the overview. `features/` holds one implementation plan per feature. `TEXTURES_NEEDED.md` lists the textures the mod is waiting on.

Each feature file uses the same sections:
- **Goal**
- **Data shape** (types, NBT, JSON)
- **Implementation** (classes, events, hooks)
- **Config surface**
- **Assets** (which vanilla textures it reuses, and which textures it needs from you)
- **Depends on** (other feature files)
- **Verification**
- **Phase**

The feature files:
- [`tacz-bridge.md`](features/tacz-bridge.md)
- [`configuration.md`](features/configuration.md)
- [`map-model-and-zones.md`](features/map-model-and-zones.md)
- [`map-validation.md`](features/map-validation.md)
- [`zone-tools-and-outlines.md`](features/zone-tools-and-outlines.md) (covers room detection and the Create-style visuals)
- [`windows-and-barricades.md`](features/windows-and-barricades.md)
- [`doors-and-debris.md`](features/doors-and-debris.md)
- [`game-loop-and-rounds.md`](features/game-loop-and-rounds.md)
- [`proximity-spawning.md`](features/proximity-spawning.md)
- [`mob-profiles-and-ai-injection.md`](features/mob-profiles-and-ai-injection.md)
- [`economy-and-points.md`](features/economy-and-points.md)
- [`wall-buys.md`](features/wall-buys.md)
- [`mystery-box.md`](features/mystery-box.md)
- [`perk-machines.md`](features/perk-machines.md)
- [`pack-a-punch.md`](features/pack-a-punch.md)
- [`downs-and-revives.md`](features/downs-and-revives.md)
- [`power-ups.md`](features/power-ups.md)
- [`power-switch.md`](features/power-switch.md)
- [`traps.md`](features/traps.md)
- [`wave-events.md`](features/wave-events.md)
- [`hud-and-prompts.md`](features/hud-and-prompts.md)
- [`sessions-and-multiplayer.md`](features/sessions-and-multiplayer.md)
- [`map-export-import.md`](features/map-export-import.md)
- [`scripting-and-events.md`](features/scripting-and-events.md)
- [`zombie-pathfinding.md`](features/zombie-pathfinding.md)
- [`procedural-generation.md`](features/procedural-generation.md) (marked post-MVP)

## Asset rule: never add textures

- **No texture files.** Never add a texture file to the repo, under any circumstances.
- **When a feature needs a custom texture,** leave it missing (the purple-and-black checkerboard). Add a row to `TEXTURES_NEEDED.md` with the path, what it's for, its size, and the feature that needs it, and tell you in the reply for that work.
- **Models may reference vanilla textures.** That isn't adding a texture, and it's the default stand-in. Window boards use a custom block model whose cuboids reference `minecraft:block/oak_planks`.
- **Known gaps:**
  - The hover face texture on outlines. Until you supply one, outlines draw as lines only.
  - Perk, power-up, and points HUD icons.
  - Power-up entity sprites.
  - Machine faces wherever a vanilla texture isn't a reasonable stand-in.

## Core design decisions

1. **One map model for hand-built and generated maps.** A `ZombiesMap` holds zones (each zone is a set of AABBs), the zone graph (doors are edges), and an index of feature positions. The map-maker tools output this structure in the MVP. The post-MVP generator will output the same structure. Everything downstream (spawning, navigation, validation, the game loop) reads only `ZombiesMap` and never asks where the map came from.
2. **Feature blocks are the source of truth for positional features.** Wall-buys, barricades, the box, perks, PaP, the power switch, traps, and spawn markers are blocks with BlockEntities that hold their own config (gun id, cost, linked zones). Saved structure NBT carries BlockEntity data. That makes map export and import simple now, and later a room template exported from a hand-built map will bring its wall-buys and windows into procedural generation at no extra cost. Each feature registers its position in `MapSavedData` so the map knows about features in unloaded chunks. Doors are the exception. A door is a group of ordinary blocks picked with the Door Wand, so its `DoorDef` (members, cost, power, zones) lives in `MapSavedData`, and its blocks are snapshotted at game start so reset can rebuild them.
3. **The map is immutable during play, except for feature block states.** Doors toggle open and closed. Barricades hold a plank count. Players are in adventure mode and no terrain is ever destroyed. Resetting a map walks the feature index and restores each default state. Reset is idempotent, so it runs on game end and again on server start whenever the last game didn't end cleanly. Games are not persisted across restarts.
4. **The game is an explicit state machine.** The states are `Lobby → Countdown → Intermission ⇄ RoundActive → GameOver`. Each player has `Alive | Downed(bleedTicks, reviver) | Spectating`, plus points, perks, and stats. All logic runs on the server, and the client only renders synced state.
5. **TacZ is behind one boundary.** Only `tacz/TaczBridge` imports `com.tacz.*`, so a TacZ update touches one package. Everything below was checked against the TacZ `1.20.1` branch source. The bridge does five jobs:
   - **Giving guns.** `GunItemBuilder.create().setId(rl).setAmmoCount(n).build()` builds the stack. `TimelessAPI.getAllCommonGunIndex()` lists every installed gun for the box pool.
   - **Reserve ammo.** It uses TacZ "dummy ammo" through `IGun.setDummyAmmoAmount` and `setMaxDummyAmmoAmount`, so no ammo items clutter the inventory. Max Ammo and wall-buy refills set these values.
   - **Points.** It listens to `EntityHurtByGunEvent.Pre` and `EntityKillByGunEvent`, server side only. These events carry the attacker, the gun id, and `isHeadShot()`, and the bridge maps them to internal `GunHit` and `GunKill` records. TacZ posts both events on the server and again on the client, so the bridge filters on `getLogicalSide()`.
   - **Damage changes.** Insta-Kill, PaP, and perk damage multipliers call `EntityHurtByGunEvent.Pre#setBaseAmount`, which TacZ reads back after the event is posted.
   - **Per-stack gun stats.** PaP mag size and Double Tap fire rate change `GunProperties` (`DAMAGE`, `ROUNDS_PER_MINUTE`, and so on) through `AttachmentPropertyEvent`. That cache class sits outside TacZ's `api` package, so this is the most fragile seam and it stays isolated in the bridge.
   - **Dependency.** The build uses `maven.modrinth:timeless-and-classics-zero:1.1.8-hotfix2` through `fg.deobf`. `mods.toml` declares a mandatory `tacz` dependency with range `[1.1.8,)`.
   - **License.** TacZ is GPL3 (assets are CC BY-NC-ND). Pick a GPL-compatible license for this mod.
6. **Configuration has three layers, all resolved into one `GameSettings` record.** The precedence is a per-map override, then the server config (`ForgeConfigSpec` SERVER, per world, synced to clients), then built-in defaults. Content definitions are datapack JSON loaded through `AddReloadListenerEvent`, so `/reload` picks up edits. That content covers mob profiles, perks, power-ups, wave events, box pools, and weapon tiers. Each definition has a codec, and the same codec serves JSON, NBT, and network sync. Making a new value configurable later means adding one codec field.
7. **Navigation uses hierarchical A\* over cached portal-to-portal paths.** The full design is in "Zombie pathfinding" below. Vanilla pathfinding fails over long distances because `maxVisitedNodes` scales with follow range. The map is immutable during play, so the paths through each room can be computed once and reused for the whole game.

## Mob-agnostic zombies (difficulty assessment)

You asked to reuse vanilla mobs (or any other mod's mobs) with injected AI. This is feasible at **moderate** difficulty for most mobs. A small set of mob kinds can't be supported cleanly.

A `MobProfile` is a datapack JSON file:
```json
{ "entity": "minecraft:zombie", "weight": 10, "rounds": {"min": 1},
  "ai": "replace", "nbt": {}, "health": "cod", "speed": {"base": 0.23, "sprint_from_round": 6},
  "tags": ["breaks_barricades"] }
```
On spawn, `SpawnDirector` creates the entity with `EntityType.create`, applies the profile's NBT, and attaches a `GameMob` capability (game id, profile, assigned window, target zone). `AiInjector` then runs:
- In `replace` mode it clears `goalSelector` and `targetSelector`. In `augment` mode it keeps the mob's own goals. Both modes then install `PortalNavigateGoal` (it follows the hierarchical path from "Zombie pathfinding"), `BreakBarricadeGoal`, `TargetGamePlayerGoal` (which skips downed players and spectators), and a melee goal when the mob has none.
- It sets attributes through `AttributeModifier`s: round-scaled health, speed, and a larger follow range.
- It sets persistence-required and suppresses sun burning, drops, and XP using `LivingDropsEvent`, `LivingExperienceDropEvent`, and fire clearing on tagged mobs.

Support tiers:
- **Works.** Any `PathfinderMob` driven by Goals. That covers most vanilla hostiles and most modded mobs. GeckoLib only renders, so GeckoLib mobs are fine.
- **Partial.** Brain-based mobs (piglin, hoglin, warden, villager). Their Brain ticks alongside goals and fights over movement. These get `augment` only, plus a load-time warning.
- **Unsupported for navigation.** Mobs that aren't `PathfinderMob` (ghast, phantom) or that rebuild their goals every tick. The profile loader warns about these. They still count as zombies and can target players.
- **Hitbox.** Windows vary in size, so `MapValidator` checks each mob's bounding box against the openings of the windows it can spawn at.

The main risk is behavior parity across arbitrary mods, not the injection code. `/pap mobtest <profile>` spawns a profile in a test pen so a pack author can check it.

## Zombie pathfinding

This is HPA\*-style hierarchical search. It lives in `features/zombie-pathfinding.md`.

**Portals and movement classes.**
- **Portals.** A portal is any opening that links two zones (a door or debris) or links the outside to a zone (a window). Portals are the nodes of the high-level graph.
- **Movement classes.** A mob's movement class is its bounding box rounded up to whole blocks (width × height), plus flags for whether it can climb or swim. Every cache is keyed by movement class, because a 1-wide zombie and a 2-wide modded mob fit through different gaps.

**Passability snapshots.** When a zone unlocks (at game start for the start zone, then whenever a door opens), we snapshot the zone into an immutable `PassabilityGrid` per movement class. Legality uses vanilla's `WalkNodeEvaluator` rules for steps, jumps, slabs, and fences. The map doesn't change during play, so A\* can run on a worker thread against the snapshot without touching the live world.

**Intra-zone edges.** When a door opens, we run block-level A\* from every portal to every other portal in each zone on both sides of the door. Each result is cached as one weighted edge `(portalA, portalB, movementClass) → (cost, nodeList)`. The cache stays valid for the whole game, because the map is immutable and opening a door only adds edges. A game reset clears it.

**Query.** For a mob in zone `Zm` chasing a player in zone `Zp`:
1. If `Zm == Zp`, use vanilla navigation directly. The leg is short.
2. Otherwise, add two temporary nodes to the portal graph: the mob, with edges to `Zm`'s open portals, and the player, with edges from `Zp`'s open portals. Those edge costs come from local A\* inside one zone and are cached briefly.
3. Run A\* over the portal graph, using the cached costs as edge weights.
4. Join the cached node lists of the chosen edges into a vanilla `Path` and hand it to `navigation.moveTo(path, speed)`.
5. Replan when the target player changes zone, or when vanilla stuck detection fires. The final leg to the player stays on vanilla navigation, so the player moving around inside a zone never triggers a replan.

**Sharing results.** Many mobs chase the same zone, so we also cache "next portal from portal P toward zone Z" per movement class. Opening a door invalidates that cache. With 24 mobs alive, most queries become map lookups.

**Heuristic.** We use 3D Manhattan distance, as you asked. It is admissible only when the cost of every path is at least the Manhattan distance between its ends. That holds if the block-level A\* moves only along axes, with no diagonal steps. Vanilla navigation does allow diagonals, so we pick one of two options:
- Run the block-level search with axis-aligned moves only. Manhattan is then admissible, and vanilla's path following smooths the corners when the mob walks it.
- Allow diagonal steps and use 3D octile distance instead.

The plan picks axis-aligned moves with Manhattan. Phase 4 includes a spike comparing it with octile, and only that spike's measurements change the decision.

**Fallbacks.** Some mobs don't use ground navigation, such as flyers or modded mobs with custom navigators. They take the portal sequence from the high-level search as waypoints and use their own navigation for each leg.

## Package layout (`src/main/java/<group>/packapunch/`)

- `config/` holds `GameSettings` (a record with a codec), the `ServerConfig` spec, and the override resolver.
- `data/` holds the reload listeners and codecs for `ItemSpec`, `PunchDef`, `MobProfile`, `PerkDef`, `PowerUpDef`, `WaveEventDef`, `BoxPool`, and `WeaponTier`. `PunchResolver` and `PunchAudit` live here too. Perk and power-up effects use codec dispatch into a small registry of effect types: `attribute_modifier`, `damage_multiplier`, `revive_speed`, `weapon_slots`, `max_ammo`, `insta_kill`, `nuke`, and so on.
- `map/` holds `ZombiesMap`, `Zone`, `Door`, `Window`, `SpawnPoint`, `MapSavedData`, `ZoneIndex` (a spatial lookup from chunk section to zone), `MapCompiler` (feature index to compiled map), and `MapValidator`. The validator checks that every zone is reachable from the start, that power and PaP are reachable, that every zone has a spawn point, and that every door links two zones.
- `game/` holds `GameManager` (one game per map level), the `Game` state machine, `RoundPlan`, `Economy`, `SpawnDirector`, `DownedSystem`, `PowerUpSystem`, `WaveEventRunner`, and the inventory snapshot and restore logic.
- `mob/` holds the `GameMob` capability, `AiInjector`, the goals, and the health and spawn-count formulas.
- `mob/path/` holds `MovementClass`, `PassabilityGrid`, `BlockAStar` (pure, runs on the grid), `PortalGraph`, `PortalPathCache`, and `HierarchicalPlanner`.
- `feature/` holds the blocks and BlockEntities.
  - **Windows have variable size.** A window is a `WindowController` BlockEntity plus every `Barricade` block in a W×H opening on one wall face. The width and height are configurable per window, from 1×1 up to a server-config max (default 5×4).
  - **Board state lives on the controller.** The controller owns the window-level board count (max boards configurable per window, default 6), its facing, and its member positions. It writes a `boards` blockstate to every member block so all of them render the same. The board models are multipart cuboids that use the vanilla oak-plank texture.
  - **Collision.** A member returns an empty collision shape to game mobs once boards reach 0 and a full shape to players, using the entity collision context.
  - **Zombie targeting and validation.** Zombies target the controller's attack face. Spawn markers link to the controller. `MapValidator` checks every mob profile that can spawn at a window against that window's opening, so a profile whose hitbox doesn't fit gets flagged per window. The other features are `WallBuy`, `MysteryBox`, `PerkMachine`, `PapMachine`, `PowerSwitch`, `TrapController`, and `SpawnMarker`. All purchases go through a single `Purchasable` path that checks distance, points, power, and state on the server.
- `tacz/` holds `TaczBridge`.
- `tools/` holds the map-maker items, the `/pap` commands, room detection, and map and room export.
- `script/` holds `GameEvents` with the sealed `GameEvent` type, `EventDef` loading, `LuaRuntime` (sandboxed globals, instruction hook, `require` searcher), and the Lua API bindings.
- `net/` holds the `SimpleChannel` packets.
- `client/` holds the HUD overlays, look-at purchase prompts, feature config screens, and `outline/ZoneOutlines` (the only place that uses Ponder).
- Datapack resources live in `src/main/resources/data/packapunch/` and cover the default mob profiles, perks, power-ups, wave events, and box pool. A void "Zombies Builder" world preset gives map makers an empty world to build in.

## Gameplay systems

- **Rounds and spawning.** Zombie count, health, speed, max alive (default 24), and spawn delay come from `GameSettings` formulas that default to CoD-like curves. Health uses a `health_scale` setting because TacZ damage is measured in Minecraft HP. Proximity spawning activates spawn points in each player's current zone and in zones within N open-door hops, weighted by distance. "Room detection" at runtime means `ZoneIndex` resolving each player's zone every few ticks.
- **Economy.** Points come from hits, kills, headshot and melee kills, and window repairs, all driven by `GunHit` and `GunKill` from `TaczBridge` and configurable per value. Double Points multiplies them.
- **Doors and debris.** A door is any group of blocks picked with the Door Wand. Clicking any member buys it, and opening sets the members to air. Each has a cost and an optional power requirement. Opening one adds its zones to the unlocked set, which grows the spawner and navigation graph.
- **Wall-buys.** A wall-buy sells an `ItemSpec`, so it can sell any item. An `ItemSpec` is an item id, a count, and optional NBT, and it has the shorthand `{"tacz_gun": "tacz:ak47"}`, which builds the stack through `TaczBridge`. Buying an item you already hold follows a `repurchase` policy:
  - `refill_ammo` is the default for TacZ guns. It refills ammo at the ammo cost, and a configurable price applies to PaP'd guns.
  - `top_up` is the default for stackables. It fills the stack back up to the spec's count.
  - `duplicate` sells another copy.
  - `deny` refuses the sale.

  The weapon-slot limit (2, or 3 with Mule Kick) counts TacZ guns only. When the slots are full, buying a new gun replaces the held one.
- **Mystery Box.** A weighted `BoxPool` of `ItemSpec`s. By default it holds every TacZ gun index entry minus the wall-buys, with per-entry overrides. The box relocates after a configurable number of uses, and Fire Sale lowers its cost.
- **Perks.** Machines reference a `PerkDef`. Perks need power, have a perk limit, and are lost on down.
- **Pack-a-Punch.** Any item can be punched if it carries the right NBT, and the machine needs power to run. The tag is a `pap` compound with two keys:
  - `punchable` is a boolean.
  - `punch_id` is optional. It names a `PunchDef` from a datapack, stored at `data/<ns>/packapunch/punch/<id>.json`.

  A `PunchDef` gives the result `ItemSpec`, an optional display name, an optional cost override, and whether to carry over ammo and attachments. The result can carry its own `pap` tag, so multi-tier upgrades work. Packapunch ships its own defaults under the `packapunch` namespace, and any datapack can override them by reusing the same id. TacZ guns created by wall-buys, the box, or the starting loadout get `punchable: true` by default (configurable per `ItemSpec`).

  A pure `PunchResolver.resolve(stack)` returns a sealed `PunchOutcome`:
  - `Replace(result)`: punchable, and `punch_id` resolves. The stack becomes the def's result.
  - `UpgradeTacz(stack)`: punchable, no `punch_id`, and the item is a TacZ gun. This applies the default TacZ upgrade: a PaP tier tag, a damage multiplier through `EntityHurtByGunEvent.Pre`, a larger mag and reserve plus RPM through `AttachmentPropertyEvent`, a rename, and a refill.
  - `Rejected(NOT_PUNCHABLE)`: the machine refuses the item.
  - `Rejected(UNKNOWN_PUNCH_ID)`: punchable, but the `punch_id` doesn't resolve.
  - `Rejected(NO_UPGRADE_PATH)`: punchable, no `punch_id`, and not a TacZ gun.

  On any `Rejected`, the player gets the same item back, is not charged, and sees the reason.

  **Load-time errors.** The last two `Rejected` cases are also caught early. On server start, after `/reload`, and in `MapValidator` before a game starts, a `PunchAudit` scans every item source the mod controls: wall-buy BlockEntities (through the feature index), box pools, the starting loadout, and every `PunchDef` result. Each bad item gets an ERROR log line with its source and position, a chat message to ops when they join, and an entry in `/pap map validate`. Items brought in from outside these sources (from chests or other mods) can't be scanned. The machine still rejects them safely at punch time.
- **Downs and revives.** Lethal damage while the player has lives puts them in `Downed` instead of killing them. They crawl, can use a pistol only, and zombies ignore them. A teammate holds use for N seconds to revive (Quick Revive shortens it), and solo Quick Revive self-revives up to 3 times. A player who bleeds out spectates and respawns next round. Food is held at 17 to disable vanilla regen, and custom CoD-style regen replaces it.
- **Power-ups.** These are floating entities dropped on kills, with a chance roll and a per-round cap. The types are Max Ammo, Insta-Kill, Double Points, Nuke, Carpenter (resets barricades), and Fire Sale.
- **Power switch and traps.** Power is a map-level flag. A trap controller is a purchasable block that outputs a redstone signal for N ticks and then cools down. What the trap does is built by the map maker with redstone.
- **Wave events.** These are `WaveEventDef`s with a trigger (round interval, specific round, or chance) and effects: a mob profile override (hellhound-style special rounds), spawn-rate multipliers, fog, and a guaranteed Max Ammo on the last kill.
- **Session handling.** Joining snapshots and clears the inventory, then gives the starting pistol and knife (gun id configurable). Leaving restores the snapshot. Late joiners spectate until the next round. A disconnect drops the player's state after a timeout.

## Map-maker tools

- **Zone Wand.** Select two corners, then `/pap zone add <zone>`. Several boxes can make up one zone.
- **Room detection.** `/pap zone detect <zone>` flood-fills the air volume from the player, stopping at solids, door members, and barricades, up to a volume cap. Greedy box merging turns the result into AABBs.
- **Door Wand.** Works like the Zone Wand, but picks blocks instead of space. Click blocks, or two corners for a box, to add them to a door group. The group shows as a Create-style cluster outline, and a block already in another door shows yellow. Zones on each side are inferred.
- **Window Tool.** Drag a rectangle on a wall face, using the same snapping Create-style selection box. Confirming fills the opening with barricade blocks and places the controller. The tool refuses openings over the configured maximum and shows them in yellow.
- **Link Tool.** It links a door to its two zones and a spawn marker to its window. The links are inferred when a door sits between two zones.
- **Feature configuration.** In creative mode, right-clicking a feature block opens a config screen for gun id, cost, perk, and so on.
- **Visualizer.** While a tool is held, the client renders zone boxes, door links, and spawn points in the Create style described in the next section.
- **`/pap map validate`** runs `MapValidator` and reports problems with their coordinates.
- **Export and import.** `/pap map export|import` handles a map pack: structure NBT, map JSON, and the map's datapack, including its EventDefs and Lua scripts.
- **Edit mode and play mode.** Markers show and games are blocked while the map is in edit mode.

## Zone visuals (Create-style outlines)

Create's glue selection is drawn by the **Outliner** in Catnip (`net.createmod.catnip.outliner`). On 1.20.1, Catnip is compiled into the **Ponder** library, which is MIT licensed and does not need Create. We depend on Ponder directly.

**Dependency.**
- Use `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92` from `https://maven.createmod.net`.
- Ponder requires Flywheel `[1.0.0,2.0)` on the client.
- Both get bundled with ForgeGradle jarJar, the same way Create bundles them, so players don't install anything extra. Use version ranges that include Create 6.0.8's pins (Ponder 1.0.91) so a modpack with Create installed resolves to one copy.
- The license requires us to ship Ponder's MIT notice.

**Boundary.** Only `client/outline/ZoneOutlines` imports `net.createmod.*`, the same pattern as `TaczBridge`. Ponder already ticks and renders the Outliner, so our code only calls `Outliner.getInstance().showAABB(slot, aabb)` and `showCluster(slot, positions)` every client tick while a tool is held. The outlines fade out on their own after 8 ticks once we stop calling.

**Behavior,** modeled on Create's `SuperGlueSelectionHandler`:
- **What shows.** While a map tool is held, every zone within range draws its boxes. The color is derived from the zone id, so it stays the same across sessions, and the lines are 1/64 wide.
- **Hover.** The zone box under the cursor is found with a ray-clip and gets 1/16 lines. The face texture comes later, once you supply one.
- **Selecting.** The first click sets a corner. The selection box then snaps to the cursor every tick. It turns yellow when the box is out of reach or overlaps another zone.
- **Room detection.** The `zone detect` result previews as a `showCluster` before you confirm it.
- **Other map elements.**
  - Door links draw as lines between the door and its two zones.
  - Spawn points and windows draw as small boxes.
  - Problems found by `MapValidator` draw in red at their positions.

## Scripting: EventDefs and sandboxed Lua (MVP)

**Event bus.** Every gameplay system reports what happens through one `GameEvents` dispatcher, using a sealed `GameEvent` type. Each event has a codec that converts it to a read-only Lua table. The MVP catalogue:
- `game_start`, `game_end`, `round_start`, `round_end`
- `zombie_spawned`, `zombie_killed` (with killer, gun or item id, and headshot)
- `player_downed`, `player_revived`, `player_bled_out`
- `wall_buy`, `mystery_box_opened`, `mystery_box_moved`, `perk_bought`, `pap_used`
- `door_opened`, `power_on`, `trap_activated`
- `powerup_spawned`, `powerup_collected`
- `wave_event_started`

In the MVP these are notifications only. Events that can be cancelled come later. When a new feature is added, emitting its event is part of finishing that feature. The Lua runtime is one subscriber, and a Forge-event adapter for Java addons can be a second subscriber later.

**EventDef files.** A datapack binds events to Lua functions with `data/<ns>/packapunch/events/<id>.json`:
```json
{ "event": "round_end", "script": "mymap:announcer", "function": "on_round_end",
  "filter": { "round_min": 5 }, "priority": 0 }
```

**Scripts.** Scripts live in `data/<ns>/packapunch/scripts/**/*.lua`, loaded by a reload listener. The script API exposed to Lua:
- `events.on(name, fn)` registers a listener at load time.
- `command(str)` runs a Minecraft command and returns its success and result. It runs through `server.getCommands().performPrefixedCommand` with a source positioned at the map origin. The permission level comes from the `scripting.command_permission_level` config (default 2, matching datapack functions).
- `require("ns:path")` loads another script. A custom searcher resolves names only against datapack scripts, never the filesystem. Modules are cached and cycles are reported.
- `log.info`, `log.warn`, and `log.error`.
- Read-only queries such as `game.round()`, `game.players()`, and `game.points(player)`.

**Sandbox.** The runtime is FiguraMC's LuaJ fork (`com.github.FiguraMC.luaj`, `3.0.8-figura`, MIT), bundled with jarJar and shared with TacZ's copy. Details are in `features/scripting-and-events.md`:
- **Hand-built globals.** Only the base, table, string, and math libs are loaded. There's no io, os, luajava, debug, coroutine, package, or binary chunk loading, and `dofile`, `loadfile`, `load`, and `string.dump` are removed.
- **Instruction budget.** A `DebugLib` subclass counts instructions for each listener call and each script load. Past the budget it throws an `Error` that `pcall` can't catch.
- **Errors.** Script errors are logged with the script and line, and sent to ops in the game. A listener that fails N times in a row is disabled.
- **Limits of the sandbox.** It protects the host machine, not the world (commands run at the configured permission level). Scripting settings are server-config only, so a map can't raise its own permissions.

**Lifecycle.** Each game gets a fresh Lua runtime built from the current datapack scripts when its lobby opens, and it is thrown away at game end. Script globals therefore never leak between games, and a `/reload` takes effect from the next game. `/pap scripts reload` rebuilds the runtime for the current game.

## Post-MVP: procedural generation

This is deferred. The MVP keeps three things so it can be added without reworking anything else:
- the generator-agnostic `ZombiesMap` model
- feature blocks that carry their config in NBT
- `MapValidator` as a pure function

The design as researched:
- **World preset.** `packapunch:zombies_map` uses a custom `ZombiesChunkGenerator`.
- **Layout.** `LayoutGenerator` is a pure, seed-deterministic room-graph builder on a grid of 16-block cells. It grows a critical path start → power → PaP, then side branches, and door costs rise with depth.
- **Validation.** `MapValidator` must accept a layout before it is used. If it doesn't, the generator retries with the next sub-seed.
- **Placement.** Each piece is placed in `applyBiomeDecoration`, clipped to the chunk. This avoids jigsaw's limits of depth 7 and 128-block radius.
- **Room authoring.** Rooms come from a `/pap room export` tool that saves part of a hand-built map as an NBT template plus socket metadata.

## Phases (each ends in a check)

0. **Scaffold and spikes.**
   - Write `PLAN.md`, the `features/` files, and an empty `TEXTURES_NEEDED.md` before writing any code. (Done.)
   - Set up the Forge 1.20.1 MDK with Parchment mappings, the TacZ dependency, and Ponder and Flywheel bundled with jarJar. Add `/pap give <gunId>`.
   - Spike the open unknowns:
     - That an `AttachmentPropertyEvent` override shows up in live fire for RPM and mag size.
     - How to read a gun's type (pistol) from `CommonGunIndex` for the downed-pistol rule.
     - That dummy ammo works with every reload type in the default gun pack.
     - Access to `goalSelector` (an access transformer if needed).
     - A forced crawl pose for downed players.
   - *Check:*
     - `runClient` and `runServer` both launch with TacZ.
     - A test Outliner box renders in the world.
     - The built jar launches both alone and alongside Create 6.0.8 without a duplicate-mod error.
     - The spike results are logged.
1. **Map model and zone tools.** (Done.)
   - Build the map model, codecs, `MapSavedData`, `ZoneIndex`, the Zone Wand with Create-style outlines, zone commands, room detection, spawn markers, and `MapValidator`.
   - *Check:* build two zones in the void builder world, see them outlined while holding the wand, and pass validation.
2. **Core loop.** (Done.)
   - Build the game state machine, `GameEvents`, `SpawnDirector` with vanilla zombies, proximity spawning, and the points economy.
   - *Check:* shoot zombies, earn points, and advance rounds on the two-zone map.
3. **Scripting.** (Done.)
   - Build `LuaRuntime` and its sandbox, `EventDef` loading, `events.on`, `command`, and `require`.
   - Every later phase emits its own events as it lands.
   - *Check:* a datapack script uses `require` to load a helper and runs `/title` on `round_end`. A script that tries to call `io.open` fails to load. A `while true do end` listener is stopped and logged while the server keeps ticking.
4. **Mob profiles and AI.** (Code and automated tests done. In-game check pending.)
   - Build `MobProfile` loading, `AiInjector`, barricades and windows, and hierarchical pathfinding: passability snapshots, the portal-to-portal cache filled on door open, and A\* over the portal graph.
   - Spike the Manhattan-versus-octile heuristic: compare path quality and nodes expanded on the test map.
   - *Check:* `/pap mobtest` with a zombie, a skeleton, and one modded mob, each breaking a window and pathing across two zones.
5. **Purchasables.**
   - Build doors with the Door Wand, the Link Tool, wall-buys, the box, perks, PaP, the power switch, feature config screens, and the HUD prompts.
6. **Survival systems.**
   - Build downs and revives, power-ups, traps, and wave events.
7. **Hardening and sharing.**
   - Build map export and import, and edit/play mode.
   - Cover multiplayer edge cases (late join, disconnect, spectate) and a perf pass with 4 players and 24 mobs.
   - Balance the config defaults. Make all assets original, and make the trademarked names (Juggernog, Pack-a-Punch) renameable through lang files before any public release.

## Verification

- JUnit covers the pure core: every `PunchResolver` outcome (including that a rejected stack comes back unchanged and costs 0 points), `PunchAudit` flagging a punchable non-TacZ item with no id and an unresolved `punch_id`, `ItemSpec` codec round-trips, `MapValidator`, `BlockAStar` (optimal path length on hand-made grids, and Manhattan never overestimating on axis-aligned grids), `HierarchicalPlanner` (it picks the shortest walk rather than the fewest doors, and an opened door adds edges without recomputing the existing ones), room-detection box merging, the round, health, and spawn formulas, `Economy`, and settings resolution order. Sandbox tests confirm that `io`, `os`, `luajava`, `debug`, `dofile`, `loadfile`, and binary `load` are unreachable. They also cover `require` cycle detection, the instruction budget stopping an infinite loop, and the listener being disabled after N errors.
- Forge GameTests (`@GameTestHolder`, `runGameTestServer`) cover barricade breaking and repair on a 1×1, a 3×2, and a max-size window (all member blocks share the board state, and the opening is passable to mobs only at 0 boards), door purchase unlocking a zone, wall-buy giving the right TacZ gun id, a down followed by a revive, and map reset restoring every feature state.
- A manual end-to-end check on `runServer` with 2 clients:
  1. Build a small 4-zone map using only the tools.
  2. Play to round 10.
  3. Use every purchasable. Include a non-gun wall-buy item whose `punch_id` turns it into another item. Also try a punchable non-TacZ item with no id: confirm the load-time ERROR was logged and that the machine hands the item back without charging.
  4. Get downed and revived.
  5. Watch a special round.
  6. Run `/reload` after a perk JSON edit.
  7. Check a script bound to `zombie_killed`, `wall_buy`, and `mystery_box_opened`: each runs its command once per occurrence.
  8. Export the map, import it into a fresh world, and play it again.
