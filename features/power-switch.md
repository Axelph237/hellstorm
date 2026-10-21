# Power switch

## Goal

A map can have a power switch. Turning it on enables perks, the upgrader, traps, and any doors marked as needing power.

## Data shape

- `power_switch` block with blockstate `on` (bool) and a `PowerSwitch` BlockEntity: `{ cost }` (default 0).
- `Game.powerOn` (bool).

## Implementation

- **Activation** through `Purchasable`: if power is off, charge `cost`, set `Game.powerOn = true`, flip every power switch block to `on`, and emit `power_on`.
- **Consumers** check `Game.powerOn` in their purchase checks: vending machines with `requires_power`, the upgrader, traps, and doors with `requiresPower`.
- **Feedback.** Powered machines change blockstate (a `powered` property) so their models can light up, using vanilla emissive-looking textures (for example `minecraft:block/redstone_lamp_on`).
- **No switch.** A map without a power switch starts with power on.
- **Reset.** `MapReset` turns power off.

## Config surface

- Per switch: cost.
- Map override: `power.start_on` (default false when a switch exists).

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
