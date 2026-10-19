# Pack-a-Punch

## Goal

The PaP machine upgrades any item that is marked punchable. A datapack can say exactly what an item becomes. TacZ guns without a definition get a default upgrade. Anything else is an error that is caught at load time and refused safely at the machine.

## Data shape

Item NBT:

```json
{ "pap": { "punchable": true, "punch_id": "mymap:ray_gun_mk2" } }
```

- `punchable` (bool) marks the item as accepted by the machine.
- `punch_id` (optional) names a `PunchDef`.

`data/<ns>/packapunch/punch/<id>.json`:

```json
{
  "result": { "tacz_gun": "tacz:m4a1", "nbt": { "pap": { "punchable": true, "punch_id": "mymap:m4_tier2" } } },
  "name": "item.mymap.m4_upgraded",
  "cost": 5000,
  "carry_over": { "ammo": false, "attachments": true }
}
```

The mod ships defaults under the `packapunch` namespace. A datapack overrides one by using the same id. A result can carry its own `pap` tag, which allows multi-tier upgrades.

```java
sealed interface PunchOutcome permits Replace, UpgradeTacz, Rejected {}
record Replace(ItemStack result, int cost) implements PunchOutcome {}
record UpgradeTacz(ItemStack upgraded, int cost) implements PunchOutcome {}
record Rejected(RejectReason reason) implements PunchOutcome {}
enum RejectReason { NOT_PUNCHABLE, UNKNOWN_PUNCH_ID, NO_UPGRADE_PATH }
```

## Implementation

**`PunchResolver.resolve(stack, punchDefs, isTaczGun)`** is pure:
1. No `pap.punchable: true`: `Rejected(NOT_PUNCHABLE)`.
2. `punch_id` present and found: `Replace(build(def.result), def.cost or default)`.
3. `punch_id` present and not found: `Rejected(UNKNOWN_PUNCH_ID)`.
4. No `punch_id`, and the item is a TacZ gun: `UpgradeTacz(...)`.
5. No `punch_id`, and not a TacZ gun: `Rejected(NO_UPGRADE_PATH)`.

**Default TacZ upgrade** (`UpgradeTacz`):
- Increments `pap.tier`.
- Damage multiplier through `EntityHurtByGunEvent.Pre`, larger magazine and reserve plus RPM through `AttachmentPropertyEvent` (see `tacz-bridge.md`).
- Renames the stack ("Upgraded " + name, lang key).
- Refills ammo.
- Removes `pap.punchable` unless `pap.max_tier` allows another tier.

**Machine** through `Purchasable`, needs power:
1. The player inserts the held item. The machine resolves it.
2. On `Rejected`, the item goes straight back, nothing is charged, and the player sees the reason.
3. On success, charge the cost, hold the item for `pap.process_ticks`, then offer the result for `pap.collect_ticks` to that player only. Emit `pap_used`.

**`PunchAudit`** reports the two `Rejected` errors before anyone plays:
- Runs on server start, after `/reload`, and inside `MapValidator` at game start.
- Scans every item source the mod controls: wall-buy BlockEntities (through the feature index), box pools, the starting loadout, and every `PunchDef` result.
- Each problem is an ERROR log line with its source and position, a chat message to ops on join, and a line in `/pap map validate`.
- Items from outside these sources (chests, other mods) can't be scanned. The machine still rejects them safely.

## Config surface

- `pap.default_cost` (5000), `pap.process_ticks` (60), `pap.collect_ticks` (240), `pap.max_tier` (1).
- TacZ multipliers in `tacz-bridge.md`.
- Per `PunchDef`: result, name, cost, carry-over.

## Assets

- Machine block uses vanilla textures in a custom model.
- **Needed from you** if you want a distinct look: a machine texture. Optional in `TEXTURES_NEEDED.md`.

## Depends on

`tacz-bridge.md`, `wall-buys.md` (`ItemSpec`), `power-switch.md`, `economy-and-points.md`, `map-validation.md`.

## Verification

- JUnit, one test per outcome. For each `Rejected` case, the returned stack equals the input and the cost is 0.
- JUnit: `PunchAudit` flags a punchable non-TacZ item with no id and a punch id that doesn't exist, and does not flag a valid one.
- JUnit: a datapack def with the same id as a `packapunch` default replaces it.
- In game: punch a non-gun wall-buy item with a `punch_id` and receive the result. Insert a punchable stick with no id: it comes back, no points are taken, and the load-time ERROR was in the log.

## Phase

5.
