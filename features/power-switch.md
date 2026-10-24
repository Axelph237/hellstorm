# Power switch

## Goal

A map can have a power switch. Turning it on enables perks, the upgrader, traps, and any doors marked as needing power.

## Data shape

- `power_switch` block, a lever whose `powered` property is the switch's state, and a `PowerSwitchBlock.Entity` block entity: `{ cost }` (default 0).
- `Game.powerOn` (bool).

## Implementation

- **Activation** through `Purchasable`: if power is off, charge `cost`, set `Game.powerOn = true`, pull every power switch in the map (`powered`), and emit `power_on`.
- **Consumers** check `Game.powerOn` in their purchase checks: vending machines with `requires_power`, the upgrader, traps, and doors with `requiresPower`.
- **Feedback.** Designed, not built: machines have no `powered` blockstate. Vending machines light up from their block entity (see As built). Everything that needs power says "No power" in its prompt.
- **No switch.** A map without a power switch starts with power on.
- **Reset.** `MapReset` turns power off.

## Config surface

- Per switch: cost.
- `power.start_on` is designed but not built. A map with a switch always starts with the power off, and one without starts with it on.

## Assets

- Lever-style model using vanilla textures (`minecraft:block/lever`, `minecraft:block/iron_block`).

## Depends on

`economy-and-points.md`, `map-model-and-zones.md`.

## Verification

- GameTest: before power, a perk, the upgrader, a trap, and a powered door all refuse. After flipping the switch, all four work.
- GameTest: a map without a switch starts powered.
- GameTest: reset turns power off.

## Phase

5.
- **Sound** (2026-10-23). Throwing the switch plays the metal door and the generator at the switch (`GameSounds.POWER_ON`), heard by players near it, not by every player wherever they are.

## As built (phase 5)

- **Block** (`feature/PowerSwitchBlock`). It extends `LeverBlock`, so it looks and acts like a lever and powers redstone when pulled; map makers can wire lights or doors to it. It has no collision and can't be broken in survival. Its block entity holds `{ cost }` (0, free), set with `/hs power cost <points>` on the looked-at switch or from its settings screen (an op in creative using it outside a game). Outside a game it never flips.
- **Buying.** In a game, using any switch sells the power through `Shop`; once it's on, it's refused with "The power is already on". Delivery calls `Game.turnPowerOn(by)`, which emits `GameEvent.PowerOn` (Lua `power_on`), plays `GameSounds.POWER_ON` at the switch, pulls every switch in the map and announces "The power is on".
- **Start.** `Game` starts powered when the map has no `POWER_SWITCH` feature and unpowered when it has one. There's no `power.start_on`.
- **Consumers.** `Game.charge(buyer, cost, needsPower)` refuses with `Refusal.NeedsPower` while the power is off, before charging. A sale needs power for a vending machine with `requires_power` (default true), a wall-buy with `requires_power` (default false), a trap with `requires_power` (default true), a door with `requires_power` (default false), and every upgrade at the upgrader. Prompts show "No power" in red.
- **Validation.** `power_switch_locked` (an error) when no switch can be reached from a start room without a door that needs power; `feature_outside_room` (a warning) for a switch in no room and beside none.
- **Reset.** `MapReset` pulls every switch back up when a game opens and ends; each game starts with its own `powerOn`.
- **Tests.** JUnit `GameTest.aMapWithAPowerSwitchStartsUnpoweredAndPowerComesOnOnce`; `MapValidatorTest.aPowerSwitchOnlyBehindADoorThatNeedsPowerIsAnError`; GameTests `PurchaseGameTests.thePowerSwitchTurnsThePowerOnForItsCostAndResetsWhenTheGameEnds`, `PerkGameTests` (power needed to vend), `PapGameTests.theMachineNeedsPower`, `TrapGameTests.aTrapThatNeedsPowerWaitsForThePower`. No test covers a door or a wall-buy that needs power.

- **No commands** (2026-10-24). `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` are gone: each machine is set on its settings screen (an op in creative using it outside a game). Anyone else sees its settings in chat, ending "An op in creative changes it by using it". `CommandTreeGameTests` checks none of them is registered.
