# Purchasables foundation (phase 5)

## Goal

Every machine and door is bought the same way, so each feature only writes what it sells. Map makers place machines as custom blocks; each block stores its own settings and travels with the map.

## Data shape

- **`FeatureKind`** (`map/`): `SPAWN`, `POWER_SWITCH`, `WALL_BUY`, `PERK_MACHINE`, `MYSTERY_BOX`, `PAP_MACHINE`. `ZombiesMap.features()` indexes every machine's position, so a game knows about machines in unloaded chunks.
- **`ConfigBlockEntity<C>`** (`feature/`): a block entity holding one codec'd settings record `C`, saved under `config` and synced to clients (`getUpdateTag`, `getUpdatePacket`). `setConfig` saves and syncs. Subclasses call `changed()` after changing their own synced state (a rolling box, a busy machine).
- **`FeatureBlock`** (`feature/`), implemented by each machine block:
  - `featureKind()`.
  - `buy(player, game, pos, state)`: called for a player in a game in this level.
  - `describe(level, pos)`: one chat line for map makers (anyone not in a game who uses the block).
  - Static helpers `use(player, pos)`, `placed(...)` (call from `onPlace`), `removed(...)` (call from `onRemove`).
- **`Prompted`** (`feature/`), implemented by a machine's block entity: `Optional<Prompt> prompt()` with `Prompt(String what, int cost, boolean needsPower)`. Read on the client from synced data. `client/PromptOverlay` draws "Press [Use] or [O] to {what} [cost]", greyed with the reason when the player can't afford it or it needs power.
- **`Refusal`** (`game/`): `NotPlaying`, `NotEnoughPoints(cost, points)`, `NeedsPower`, `Because(message)`.
- **`Shop.Sale`** (`game/`): `cost()`, `needsPower()`, `check(game, player)` (feature rules, before charging), `deliver(game, player)` (a refusal here refunds).
- **`ItemSpec`** (`data/`): `Plain(item, count, nbt)` or `Gun(tacz_gun, unlimited_ammo, punchable = true)`. `build(reserveMagazines)`, `matches(stack)`, `describe()`. Constants `PAP`, `PUNCHABLE`, `PUNCH_ID` name the `pap` NBT compound.
- **Settings** (`GameSettings`): `players.weapon_slots` (2), `players.perk_limit` (4), `doors.default_cost` (750), `box.*` (cost 950, fire sale 10, roll 80 ticks, collect 240, moves after 4 uses at 0.15 per roll), `pap.*` (cost 5000, process 60, collect 240, max tier 1, damage x2.0, magazine x1.5, rpm x1.2 per tier).
- **`PlayerState.bonusWeaponSlots`**: extra gun slots from perks (Mule Kick).

## Implementation

- **Buying.** `Shop.buy(player, game, sale)`:
  1. `sale.check`, then `Game.charge(player, cost, needsPower)`, which refuses strangers, downed players, a game that's over, no power, or too few points.
  2. Then `sale.deliver`. A refusal there refunds the cost.
  3. A refusal shows on the action bar with a "no" sound. Success plays a pickup sound.
- **Power.** `Game.powerOn()` starts true when the map has no `POWER_SWITCH` feature. `Game.turnPowerOn(by)` emits `power_on` once.
- **Using a machine.** Right click calls the block's `use`, which calls `FeatureBlock.use`. The Pack-a-Punch use key (`client/UseKey`, default O, the same key as TacZ Interact) sends `UsePacket(pos, firstPress)`. A machine is used only on the first packet of a press; barricades repair while the key is held. The server checks reach.
- **HUD sync.** Each status update (every 10 ticks) also sends `HudSyncPacket(points, powerOn)`. The client treats a status more than 2 seconds old as "not in a game".
- **Reset.** `MapReset.reset(level, map)` runs when a game is created and when it ends: it restores windows and turns power switches off. Each feature adds its own reset there.
- **Commands.** `/pap <feature> ...` subcommands act on the machine the player is looking at, through `command/Targets.lookedAt(player, BlockEntityClass, "name")`.
- **Events.** A new event is a `GameEvent` record plus its name and Lua table in `script/LuaEvents` (`name` and `table`).
- **Power switch** (`feature/PowerSwitchBlock`): a `LeverBlock` only a game can pull. Settings `{ cost }` (default 0), set with `/pap power cost <points>`. Pulling one charges the cost, turns the power on, flips every switch in the map, and powers redstone like a lever. It reuses vanilla's lever models.

## Assets

- The power switch uses `minecraft:block/lever` and `lever_on`. No new textures.

## Verification

- JUnit: `GameTest` covers charging (refusal charges nothing, refund, strangers) and power (a map with a switch starts off, power comes on once). `ItemSpecTest` covers both spec forms, defaults, round trips, and invalid values.
- GameTest `PurchaseGameTests.thePowerSwitchTurnsThePowerOnForItsCostAndResetsWhenTheGameEnds`:
  - A refusal for too few points.
  - The purchase, with every switch flipping and redstone powered.
  - No second charge.
  - A reset when the game ends.
- Helpers for further tests: `PurchaseGameTests.game(helper, player, featurePositions...)`, `player(helper, name)`, `points(active, player)`.

## Phase

5.
