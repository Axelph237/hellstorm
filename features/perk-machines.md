# Perk machines

## Goal

Machines sell perks like Juggernog, Speed Cola, Double Tap, Quick Revive, Stamin-Up, and Mule Kick. Perks are data-driven, need power, and are lost when the player goes down.

## Data shape

`data/<ns>/packapunch/perks/<id>.json`:

```json
{
  "name": "perk.packapunch.juggernog",
  "cost": 2500,
  "requires_power": true,
  "color": "#d33a2c",
  "effects": [
    { "type": "attribute_modifier", "attribute": "minecraft:generic.max_health", "amount": 20, "operation": "addition" }
  ]
}
```

Effect types (codec dispatch on `type`):
- `attribute_modifier` (Juggernog health, Stamin-Up speed)
- `reload_speed` (Speed Cola, through `TaczBridge`; how TacZ exposes reload time is checked in phase 5)
- `rpm_multiplier` (Double Tap, through `AttachmentPropertyEvent`)
- `damage_multiplier`
- `revive_speed` and `self_revive` (Quick Revive)
- `weapon_slots` (Mule Kick)

`PerkMachine` BlockEntity: `{ perkId }`.

## Implementation

- **Buying** through `Purchasable`: refuse without power (if required), if the player already has it, or at `players.perk_limit`. On success, add to `PlayerState.perks` and apply each effect. Emit `perk_bought`.
- **Effects** implement `apply(PlayerState, ServerPlayer)` and `remove(...)`. Attribute effects use a UUID per perk so removal is exact.
- **Losing perks.** Going `Downed` removes every perk (Quick Revive's solo self-revive is handled in `downs-and-revives.md`).
- **Names.** Perk names are lang keys, so trademarked names can be renamed in a resource pack.

## Config surface

- Per perk JSON: cost, power requirement, effects.
- `players.perk_limit` (4).

## Assets

- Machine block uses vanilla textures in a custom model, tinted by the perk's `color`.
- **Needed from you:** one HUD icon per default perk (16×16). Listed in `TEXTURES_NEEDED.md`.

## Depends on

`economy-and-points.md`, `power-switch.md`, `tacz-bridge.md`, `downs-and-revives.md`.

## Verification

- JUnit: each effect type applies and removes cleanly (attribute value returns to the original).
- GameTest: Juggernog raises max health, going down removes it, and buying it again works.
- GameTest: a power-gated perk refuses until power is on. The perk limit refuses the fifth perk.
- In game: edit Juggernog's amount, `/reload`, start a new game, and see the new value.

## Phase

5.
