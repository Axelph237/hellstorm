# Purchasables foundation (phase 5)

## Goal

Every machine and door is bought the same way, so each feature only writes what it sells. Map makers place machines as custom blocks; each block stores its own settings and travels with the map.

## Data shape

- **`FeatureKind`** (`map/`): `SPAWN`, `POWER_SWITCH`, `WALL_BUY`, `VENDING_MACHINE` (saved as `perk_machine`), `MYSTERY_BOX`, `PAP_MACHINE`. `ZombiesMap.features()` indexes every machine's position, so a game knows about machines in unloaded chunks.
- **`ConfigBlockEntity<C>`** (`feature/`): a block entity holding one codec'd settings record `C`, saved under `config` and synced to clients (`getUpdateTag`, `getUpdatePacket`). `setConfig` saves and syncs. Subclasses call `changed()` after changing their own synced state (a rolling box, a busy machine).
- **`FeatureBlock`** (`feature/`), implemented by each machine block:
  - `featureKind()`.
  - `buy(player, game, pos, state)`: called for a player in a game in this level.
  - `describe(level, pos)`: one chat line for map makers (anyone not in a game who uses the block).
  - Static helpers `use(player, pos)`, `placed(...)` (call from `onPlace`), `removed(...)` (call from `onRemove`).
- **`Prompted`** (`feature/`), implemented by a machine's block entity: `Optional<Prompt> prompt()` with `Prompt(String label, int cost, boolean needsPower)`. Read on the client from synced data. `client/PromptOverlay` draws "{label} [{cost}]" with gold brackets and the cost in red when the player can't afford it, or "No power" in red while it needs the power on.
- **`Refusal`** (`game/`): `NotPlaying`, `NotEnoughPoints(cost, points)`, `NeedsPower`, `Because(message)`.
- **`Shop.Sale`** (`game/`): `cost()`, `needsPower()`, `check(game, player)` (feature rules, before charging), `deliver(game, player)` (a refusal here refunds).
- **`ItemSpec`** (`data/`): `Plain(item, count, nbt)` or `Gun(tacz_gun, unlimited_ammo, punchable = true)`. `build(reserveMagazines)`, `matches(stack)`, `describe()`. Constants `PAP`, `PUNCHABLE`, `PUNCH_ID` name the `pap` NBT compound.
- **Settings** (`GameSettings`): `players.weapon_slots` (2), `players.perk_limit` (4), `doors.default_cost` (750), `box.*` (cost 950, Fire Sale 10, roll 80 ticks, collect 240, moves after 4 uses at 0.15 per roll), `pap.*` (cost 5000, process 60, collect 240, max tier 1, damage x2.0, magazine x1.5, rpm x1.2 per tier).
- **`hellstorm:extra_weapon_slots`** (a player attribute): extra gun slots, from the Off-Hand effect.

## Implementation

- **Buying.** `Shop.buy(player, game, sale)`:
  1. `sale.check`, then `Game.charge(player, cost, needsPower)`, which refuses strangers, downed players, a game that's over, no power, or too few points.
  2. Then `sale.deliver`. A refusal there refunds the cost.
  3. A refusal shows on the action bar with a "no" sound, except too few points, which is silent. A purchase that spent points plays the cha-ching (`Shop.chaChing`, sound event `hellstorm:cha_ching`); one that cost nothing (taking a finished upgrade, a free power switch) plays the old pickup chime, since no points moved.
- **Cha-ching.** Played with `playNotifySound`, so only the buyer hears it, at their position, as in CoD: the points are theirs, and in a busy room everyone's purchases at full volume would be noise for the rest. It plays at the purchase, when the points go, so the vending machine's comes as you press, not when it hands the item over 1.6 seconds later. The same sound plays to a player whose rebuilt board paid (see `windows-and-barricades.md`).
  - **The file.** `sounds/cha_ching.ogg` is your `cha_ching.mp3` made mono (the two channels averaged; they were all but identical) as Ogg Vorbis, since Minecraft only places mono sounds in the world. It runs from 5 ms before the first strike (0.11 s into the recording; the silence before it is cut) to where the bell's ring falls 50 dB under the peak (1.55 s in), with the last 0.25 s faded out: 1.44 seconds. Scaled to peak at 0.89, just under full scale, so encoding doesn't clip it. Subtitle "Cha-ching" (`subtitles.hellstorm.cha_ching`).
  - **Tests.** GameTest `PurchaseGameTests.theBuyerAloneHearsTheChaChingWhenPointsAreSpent` (with `ListeningPlayer`, a fake player that keeps the sounds played to it alone): a purchase short of points and a free one play nothing to the buyer, a paid one plays one cha-ching, and a teammate hears none.
  - **Check in game:** the cha-ching's volume against guns and the vending machine, and that it sounds once per purchase (doors, debris, wall-buys and ammo, perks, the Mystery Crate, the upgrader, traps, a paid power switch).
- **Power.** `Game.powerOn()` starts true when the map has no `POWER_SWITCH` feature. `Game.turnPowerOn(by)` emits `power_on` once.
- **Using a machine.** Right click calls the block's `use`, which calls `FeatureBlock.use`. The Hellstorm use key (`client/UseKey`, default O, the same key as TacZ Interact) sends `UsePacket(pos, firstPress)`. A machine is used only on the first packet of a press; barricades repair while the key is held. The server checks reach.
- **HUD sync.** Each status update (every 10 ticks) also sends `HudSyncPacket(points, powerOn)`. The client treats a status more than 2 seconds old as "not in a game".
- **Reset.** `MapReset.reset(level, map)` runs when a game is created and when it ends: it restores windows and turns power switches off. Each feature adds its own reset there.
- **Commands.** `/hs <feature> ...` subcommands act on the machine the player is looking at, through `command/Targets.lookedAt(player, BlockEntityClass, "name")`.
- **Settings screens.** An op in creative mode who uses a machine outside a game gets its settings screen instead of the chat line (`feature/MachineSettings`):
  - `OpenSettingsPacket` carries what only the server knows: the box pools to choose from, the item in hand (for "Sell held" on a wall-buy), and the default cost a blank cost falls back to.
  - The screens (`client/settings/`) edit the machine's own `Config` record. Rows that depend on another row (the wall-buy's ammo costs) are laid out again on the next tick through `SettingsScreen.relayout`, not inside the click. Done encodes it with the block entity's codec and sends `SaveSettingsPacket`. The server re-checks op, creative mode, and reach, then calls `ConfigBlockEntity.trySet`, and answers with `SettingsResultPacket`: the screen closes, or stays open with the reason in red.
  - `trySet` runs the machine's `problem` check (unloaded box pool, unknown gun or item, refilling ammo on a non-gun). The `/hs` commands go through the same `trySet`, so the screen and the commands accept exactly the same settings.
  - Wall-buy: "Sell held" switches it to the item in hand when the screen opened (map makers find items in the creative menu); cost; what buying again does; ammo and upgraded-ammo costs, shown only when buying again refills ammo. Wall-buys and perk machines also have "Needs power". Mystery Crate: pool and cost (blank is the default). Upgrader: cost (blank is the default). Power switch: cost.
- **Events.** A new event is a `GameEvent` record plus its name and Lua table in `script/LuaEvents` (`name` and `table`).
- **Power switch** (`feature/PowerSwitchBlock`): a `LeverBlock` only a game can pull. Settings `{ cost }` (default 0), set with `/hs power cost <points>`. Pulling one charges the cost, turns the power on, flips every switch in the map, and powers redstone like a lever. It reuses vanilla's lever models.

## Assets

- The power switch uses `minecraft:block/lever` and `lever_on`. No new textures.

## Verification

- JUnit: `GameTest` covers charging (refusal charges nothing, refund, strangers) and power (a map with a switch starts off, power comes on once). `ItemSpecTest` covers both spec forms, defaults, round trips, and invalid values.
- GameTest `SettingsGameTests`: each machine refuses settings its command would refuse and keeps the old ones, accepts good ones, and a player who isn't an op can't open the screen or save. Removing the op check from `MachineSettings.save` fails it.
- JUnit `PacketRoundTripTest.settings` covers the three settings packets.
- GameTest `PurchaseGameTests.thePowerSwitchTurnsThePowerOnForItsCostAndResetsWhenTheGameEnds`:
  - A refusal for too few points.
  - The purchase, with every switch flipping and redstone powered.
  - No second charge.
  - A reset when the game ends.
- Helpers for further tests: `PurchaseGameTests.game(helper, player, featurePositions...)`, `player(helper, name)`, `points(active, player)`.

## Phase

5.
