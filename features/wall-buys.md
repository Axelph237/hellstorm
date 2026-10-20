# Wall-buys

## Goal

A wall-buy sells any item for points. TacZ guns are the main case, with ammo refills on repeat purchase.

## Data shape

`ItemSpec` (shared with the Mystery Box and PaP):

```json
{ "item": "minecraft:golden_apple", "count": 2, "nbt": { "pap": { "punchable": true, "punch_id": "mymap:god_apple" } } }
{ "tacz_gun": "tacz:ak47" }
```

The `tacz_gun` shorthand builds the stack through `TaczBridge.createGun`. TacZ guns built this way get `pap.punchable: true` unless the spec sets it to false. A spec can add `"unlimited_ammo": true` for an endless reserve (the magazine still needs reloading); see `tacz-bridge.md`.

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

## As built (phase 5)

- **Classes.** `feature/WallBuy` holds the pure rules: `Repurchase`, `Config` (codec'd, the block entity's settings), and `decide(config, owned)`, which returns `Buy`, `Refill`, `TopUp(missing)`, or `Deny`. `feature/WallBuyBlock` applies them through `Shop`. `game/WeaponSlots` is the gun-slot rule, shared with the Mystery Box.
- **Block.** A thin directional plate (`FACING` points away from the wall), placed against the face you click. Its model is one 1/16-thick element textured `minecraft:block/dark_oak_planks`. `client/WallBuyRenderer` draws the item on the face the way an item frame does (`ItemDisplayContext.FIXED`, scale 0.8). The server builds the display stack and syncs it, so clients never build TacZ guns themselves. No new textures.
- **Config.** `{ item, cost, repurchase, ammo_cost, pap_ammo_cost }`. If `repurchase` is missing, it defaults by item kind. `ammo_cost` defaults to half of `cost`, and `pap_ammo_cost` to 4500. `wallbuys.default_pap_ammo_cost` isn't a `GameSettings` field yet; the 4500 is `WallBuy.DEFAULT_PAP_AMMO_COST`. An unconfigured wall-buy sells `tacz:ak47` for 1200.
- **Commands** (no config screen; all act on the looked-at wall-buy): `/pap wallbuy set <cost> held` (a TacZ gun in your main hand becomes a `tacz_gun` spec, keeping unlimited ammo; any other item becomes a plain spec with its count and NBT), `/pap wallbuy set <cost> gun <gunId>`, `/pap wallbuy ammo <cost> [papCost]`, `/pap wallbuy repurchase <refill_ammo|top_up|duplicate|deny>`, and `/pap wallbuy describe`. `set` resets `repurchase` to the item kind's default and the ammo costs to their defaults. `refill_ammo` is refused for non-guns. A `refill_ammo` config on a plain item (hand-edited NBT) acts as `top_up`.
- **Owning.** The rule looks at the held stack first, then the rest of the inventory and the offhand. TOP_UP counts every matching stack and adds the missing count. A full stack is refused and nothing is charged. PaP detection reads `pap.tier > 0`.
- **Refusals charge nothing.** Weapon slots, inventory room, and an uninstalled item are checked before charging. A gun at the slot limit replaces the gun in the selected hotbar slot. If the player isn't holding a gun, they get "Hold the gun you want to swap".
- **Prompt.** The client reads the local player's inventory (`feature/Viewer`, set at client setup) and runs the same `decide`. So the prompt shows "buy AK-47", "buy ammo for AK-47", "buy upgraded ammo for AK-47", "top up Golden Apple", or nothing for a `deny` item you already have.
- **Event.** `GameEvent.WallBuy(player, item, cost, refill)`, Lua `wall_buy` with `player`, `item` (the spec's id, such as `tacz:ak47`), `cost`, and `refill`. `GameEvents.emit` is now public, so features outside `game/` can emit events.
- **Not built.** The `PunchAudit` check of wall-buy items (Pack-a-Punch's job).
- **Tests.** JUnit: `WallBuyTest` (every repurchase decision, defaults, and the codec), `WeaponSlotsTest`. GameTests in `WallBuyGameTests`: buying a gun (id, charge, event), a refill at the ammo cost after emptying the gun, a refill at the PaP ammo cost, TOP_UP golden apples, DENY, too few points, the slot limit swapping the held gun, a refusal when not holding a gun, and a Mule Kick bonus slot.
