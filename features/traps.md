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
- GameTest: `signalStrength` 5 carries exactly 4 blocks of dust.
- GameTest: reset leaves the trap unpowered and `READY`.

## Phase

6.

## Later

Built-in damage traps (a damage volume with particles) may come back as an optional trap type once the redstone version is in use.
