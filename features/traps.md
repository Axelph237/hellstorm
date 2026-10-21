# Traps

## Goal

Players pay to activate a trap. For now, activating a trap only outputs a redstone signal. What the trap actually does is up to the map maker, who builds it from redstone and vanilla blocks: dispensers of lava, pistons, TNT, fire, a flooding corridor, whatever they want.

## Data shape

`trap_controller` block with blockstate `powered` (bool) and `facing`, plus a `TrapController` BlockEntity:

```java
record TrapConfig(
    String displayName,
    int cost,
    boolean requiresPower,     // needs the map's power switch on
    int activeTicks,           // how long the redstone signal stays on
    int cooldownTicks,
    int signalStrength) {}     // 1 to 15
```

Runtime state: `READY | ACTIVE(ticksLeft) | COOLDOWN(ticksLeft)`.

## Implementation

- **Activation** goes through the common `Purchasable` path. It needs the trap to be `READY` and, if `requiresPower` is set, the map's power to be on. It then:
  1. Charges the cost.
  2. Switches to `ACTIVE` and sets `powered = true`.
  3. Emits `trap_activated` (see `scripting-and-events.md`), so Lua scripts can add effects too.
- **Redstone output.** While `powered`, the block acts like a held button.
  - `isSignalSource` is true. `getSignal` and `getDirectSignal` return `signalStrength` on every side, so it strongly powers the block it's attached to.
  - It calls `updateNeighborsAt` on itself and on the block behind it when it turns on and when it turns off.
- **Cooldown.** After `activeTicks`, the trap sets `powered = false` and switches to `COOLDOWN`. When the cooldown ends it returns to `READY`.
- **Points.** Mobs killed by the contraption die from non-player damage, so they award no points. This follows the rule in `economy-and-points.md` and needs no special case.
- **Reset.** `MapReset` sets every trap to `READY`, unpowered.
- **Authoring.** Place the controller and wire it to anything. Sneak-right-click with a map tool to open its config screen. While a tool is held, the visualizer shows each controller with a small box and its state color.

## Config surface

- Per trap: name, cost, power requirement, active ticks, cooldown ticks, signal strength.
- `traps.default_cost` (1000), `traps.default_active_ticks` (200), `traps.default_cooldown_ticks` (600).

## Assets

- The controller model uses vanilla textures (for example `minecraft:block/lever` on `minecraft:block/iron_block`). It shows a lit state using `minecraft:block/redstone_lamp_on`.

## Depends on

`economy-and-points.md`, `power-switch.md`, `scripting-and-events.md`.

## Verification

- GameTest: buying a trap powers a redstone lamp placed next to it, and a lamp at the end of a dust line, for exactly `activeTicks`, then both turn off.
- GameTest: the trap refuses while active or on cooldown, works again afterward, and refuses without power when `requiresPower` is set.
- GameTest: `signalStrength` 5 carries exactly 5 blocks of dust (the dust next to the trap gets 5, the fifth gets 1).
- GameTest: reset leaves the trap unpowered and `READY`.

## As built

- **Block.** `feature/TrapControllerBlock` (`hellstorm:trap_controller`, "Trap Controller" in the map tools tab): a full block with `facing` and `powered`. It's a `FeatureBlock` with a `ConfigBlockEntity<Config>`, indexed as `FeatureKind.TRAP`. Like the other machines, it can't be broken outside creative and drops nothing.
- **Settings** (`Config`):
  - `cost`, `active_ticks`, and `cooldown_ticks` are optional. Unset, they fall back to `traps.default_cost`, `traps.default_active_ticks`, and `traps.default_cooldown_ticks`.
  - `requires_power` defaults to true, and `signal_strength` (1 to 15) defaults to 15.
  - `name` (optional, 1 to 32 characters) is what players see. Without one, the label is "Trap".
  - Set them with these commands, or with the settings screen (`client/settings/TrapScreen`). Both go through `ConfigBlockEntity.trySet`.
    - `/hs trap name <text|default>`
    - `/hs trap cost|active|cooldown <n|default>`
    - `/hs trap power <bool>`
    - `/hs trap signal <1-15>`
    - `/hs trap describe`
- **Cycle** (`feature/TrapCycle`, pure): `READY`, then `ACTIVE` until a game time, then `COOLDOWN` until a game time.
  - The block entity's server ticker moves it from phase to phase.
  - If a tick comes late, the cooldown still counts from when the signal should have ended.
  - A cooldown of 0 goes straight back to ready.
  - The cycle is saved and synced.
- **Prompt.** "<name> [cost]" while ready, and no prompt while it's running or cooling down. Using it then says "<name> is already running" or "<name> is cooling down (Ns)".
- **Buying** goes through `Shop.buy`. The sale checks the refusals above and takes `needsPower` from `requires_power`. Delivering calls `activate` and emits `GameEvent.TrapActivated(player, pos)`.
- **Redstone.**
  - While `powered`, `getSignal` and `getDirectSignal` return `signal_strength` on every side, so it strongly powers every block it touches.
  - Turning on or off updates its own neighbours and the neighbours of each adjacent block. That way, blocks it strongly powers pass the change on.
  - It gives off light level 9 while on.
- **Reset.** `MapReset` calls `TrapControllerBlock.resetAll` for every `TRAP` feature, which leaves each trap ready with its signal off.
- **Model.** Vanilla textures only: an `orientable` iron block with a redstone lamp face (`redstone_lamp`, or `redstone_lamp_on` while powered). The face points at the player who placed it.
- **Look.** A map maker in creative, outside a game, uses the trap with a block in hand to draw the trap as that block on every side, lamp face included, so it blends into a wall. Players find it by its prompt. Sneaking and using it with an empty hand puts its own look back. The look is the block as it would be placed from that click, so logs keep the axis of the face clicked. Only full, solid blocks are accepted (`TrapControllerBlock.canLookLike`: solid-rendering, drawn by a block model, not another machine), so a trap never shows holes; anything else is refused with a notice. The look is saved on the block entity as `look` (a block state), so it travels with map exports, and the describe line names it.
  - Client: `client/TrapLooks` wraps each trap state's baked model. With a look in the model data (`Entity.LOOK_PROPERTY`, from `getModelData`), it returns the look block's quads for every side, in the look's render types. Its color handler hands the look's tint through, so grass and leaves get their biome color, and break particles use the look's texture. When a new look arrives from the server, the block entity asks for fresh model data and has the block redrawn.
- **Map tools.** While a map tool is held, each trap in range gets an outline: green when ready, red when running, orange when cooling down.
- **Tests.**
  - JUnit `TrapCycleTest` covers the timing.
  - GameTests in `TrapGameTests`:
    - A bought trap charges, emits `trap_activated`, and keeps two lamps lit for exactly `active_ticks` ticks: one next to it and one at the end of a dust line. Then it cools down and the lamps go out.
    - It refuses while running and while cooling down. Once ready it prompts again and sells again. Ending the game turns it off and makes it ready.
    - A named trap prompts with its name, including when it's saved through the settings screen's codec path. A name over 32 characters is refused and the old one stays. Clearing it goes back to "Trap".
    - A trap that needs power refuses until the power is on.
    - At signal 5, the first dust carries 5, a lamp after 5 dust lights, and a lamp after 6 dust stays dark.
    - A reset, run twice, leaves it ready and off, with its lamp dark.
    - A look is set through `BlockState.use` as a click does: not by a survival player; by a creative one with stone bricks or an oak log; glass and a stone slab are refused and the old look stays; the look survives save and load; sneaking with an empty hand clears it. How it's drawn isn't covered.

## Phase

6.

## Later

Built-in damage traps (a damage volume with particles) may come back as an optional trap type once the redstone version is in use.
