# Wall-buys

## Goal

A wall-buy sells any item for points. TacZ guns are the main case, with ammo refills on repeat purchase.

## Data shape

`ItemSpec` (shared with the Mystery Box and PaP):

```json
{ "item": "minecraft:golden_apple", "count": 2, "nbt": { "pap": { "punchable": true, "punch_id": "mymap:god_apple" } } }
{ "tacz_gun": "tacz:ak47" }
```

The `tacz_gun` shorthand builds the stack through `TaczBridge.createGun`. TacZ guns built this way get `pap.punchable: true` unless the spec sets it to false.

`WallBuy` BlockEntity:

```java
record WallBuyConfig(
    ItemSpec item,
    int cost,
    Repurchase repurchase,      // REFILL_AMMO, TOP_UP, DUPLICATE, DENY
    int ammoCost,               // REFILL_AMMO only
    int papAmmoCost) {}         // REFILL_AMMO on a PaP'd gun
```

Default `repurchase`: `REFILL_AMMO` for TacZ guns, `TOP_UP` for stackables, `DENY` otherwise.

## Implementation

- **Buying** through `Purchasable`:
  - If the player doesn't hold a matching item, charge `cost` and give the item.
    - For TacZ guns, enforce the weapon-slot limit (2, or 3 with Mule Kick). If full, the held gun is replaced.
    - Other items go to the inventory and are refused if it's full (no charge).
  - If the player already holds one, apply `repurchase`:
    - `REFILL_AMMO`: charge `ammoCost` (or `papAmmoCost` if PaP'd) and call `TaczBridge.refillAmmo`.
    - `TOP_UP`: charge `cost` and fill the stack back to the spec's count.
    - `DUPLICATE`: charge `cost` and give another.
    - `DENY`: refuse.
- **Matching.** TacZ guns match by gun id (ignoring PaP state for refills). Other items match by item id and the spec's NBT.
- **Display.** The wall shows the item: an item-frame-style renderer draws the stack on the block face. No texture.
- **Events.** Emits `wall_buy` with the item, cost, and whether it was a refill.
- **Audit.** `PunchAudit` checks each wall-buy's item (see `pack-a-punch.md`).

## Config surface

- Per wall-buy: item, cost, repurchase, ammo cost, PaP ammo cost (creative config screen).
- `wallbuys.default_pap_ammo_cost` (4500).
- `players.weapon_slots` (2).

## Assets

- The block renders the sold item. The wall plate uses a vanilla texture (for example `minecraft:block/dark_oak_planks`).

## Depends on

`tacz-bridge.md`, `economy-and-points.md`, `pack-a-punch.md`.

## Verification

- GameTest: buying a `tacz_gun` wall-buy gives a stack whose `GunId` matches and charges the cost.
- GameTest: buying again refills ammo at the ammo cost.
- GameTest: a golden apple wall-buy with `TOP_UP` refills to its count.
- GameTest: a `DENY` item refuses and charges nothing.
- GameTest: with 2 guns held, buying a third replaces the held one.

## Phase

5.
