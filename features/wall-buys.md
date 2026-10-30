# Wall-buys

## Goal

A wall-buy sells any item for points. TacZ guns are the main case, with ammo refills on repeat purchase.

## Data shape

`ItemSpec` (shared with the Mystery Crate and the upgrader):

```json
{ "item": "minecraft:golden_apple", "count": 2, "nbt": { "pap": { "punchable": true, "punch_id": "mymap:god_apple" } } }
{ "tacz_gun": "tacz:ak47" }
"lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}"
```

A string is an item written the way `/give` takes it, with SNBT after the id. A `tacz_gun` object also takes `nbt`, merged onto the built gun.

The `tacz_gun` shorthand builds the stack through `TaczBridge.createGun`. TacZ guns built this way get `pap.punchable: true` unless the spec sets it to false. A spec can add `"unlimited_ammo": true` for an endless reserve (the magazine still needs reloading); see `tacz-bridge.md`.

`WallBuy` BlockEntity:

```java
record WallBuyConfig(
    ItemSpec item,
    int cost,
    Repurchase repurchase,      // REFILL_AMMO, TOP_UP, DUPLICATE, DENY
    int ammoCost,               // REFILL_AMMO only
    int papAmmoCost,            // REFILL_AMMO on an upgraded gun
    boolean requiresPower) {}   // false; vending machines default to true
```

Default `repurchase`: `REFILL_AMMO` for TacZ guns, `TOP_UP` for stackables, `DENY` otherwise.

## Implementation

- **Buying** through `Purchasable`:
  - If the player doesn't hold a matching item, charge `cost` and give the item.
    - For TacZ guns, enforce the weapon-slot limit (2, or 3 with Off-Hand). If full, the held gun is replaced.
    - Other items go to the inventory and are refused if it's full (no charge).
  - If the player already holds one, apply `repurchase`:
    - `REFILL_AMMO`: charge `ammoCost` (or `papAmmoCost` if upgraded) and call `TaczBridge.refillAmmo`. A full gun, or one with unlimited ammo, is refused with no charge.
    - `TOP_UP`: charge `cost` and fill the stack back to the spec's count.
    - `DUPLICATE`: charge `cost` and give another.
    - `DENY`: refuse.
- **Matching.** TacZ guns match by gun id (ignoring upgrades for refills). Other items match by item id and the spec's NBT.
- **Power.** With `requiresPower`, the sale is refused until the power is on.
- **Potions.** A potion whose effects the player all has already is refused. A potion that would add a perk past `players.perk_limit` is refused before charging (see `perk-machines.md`).
- **Display.** The wall shows the item: an item-frame-style renderer draws the stack on the block face. No texture.
- **Events.** Emits `wall_buy` with the item, cost, and whether it was a refill.
- **Audit.** `PunchAudit` checks each wall-buy's item (see `upgrader.md`).

## Config surface

- Per wall-buy: item, cost, repurchase, ammo cost, upgraded ammo cost, needs power (creative config screen).
- The upgraded ammo cost defaults to 4500 (`WallBuy.DEFAULT_PAP_AMMO_COST`).
- Per item, datapack purchase defaults (`data/<ns>/hellstorm/gun_purchases/<gun>.json`, `item_purchases/<item>.json`): the cost, repurchase and ammo costs a machine starts with when set to sell it.
- `players.weapon_slots` (2).

## Assets

- The block renders the sold item. The wall plate uses a vanilla texture (for example `minecraft:block/dark_oak_planks`).

## Depends on

`tacz-bridge.md`, `economy-and-points.md`, `upgrader.md`.

## Verification

- GameTest: buying a `tacz_gun` wall-buy gives a stack whose `GunId` matches and charges the cost.
- GameTest: buying again refills ammo at the ammo cost.
- GameTest: a golden apple wall-buy with `TOP_UP` refills to its count.
- GameTest: a `DENY` item refuses and charges nothing.
- GameTest: with 2 guns held, buying a third replaces the held one.

## Phase

5.

## As built (phase 5)

- **Classes.** `feature/WallBuy` holds the pure rules: `Repurchase`, `Config` (codec'd, the block entity's settings), and `decide(config, owned)`, which returns `Buy`, `Refill`, `TopUp(missing)`, or `Deny`. `feature/WallBuyBlock` applies them through `Shop`. `game/WeaponSlots` is the gun-slot rule, shared with the Mystery Crate.
- **Block.** A thin directional plate (`FACING` points away from the wall), placed against the face you click. `client/WallBuyRenderer` draws a 1/16-thick plate of `minecraft:block/dark_oak_planks` sized to the item (see Plate size below) and the item on its face the way an item frame does (`ItemDisplayContext.FIXED`, scale 0.8). The server builds the display stack and syncs it, so clients never build TacZ guns themselves. No new textures.
- **Config.** `{ item, cost, repurchase, ammo_cost, pap_ammo_cost, requires_power }`. If `repurchase` is missing, it defaults by item kind. `ammo_cost` defaults to half of `cost`, `pap_ammo_cost` to 4500, and `requires_power` to false. `wallbuys.default_pap_ammo_cost` isn't a `GameSettings` field yet; the 4500 is `WallBuy.DEFAULT_PAP_AMMO_COST`. An unconfigured wall-buy sells `tacz:ak47` for 1200.
- **Commands** (all act on the looked-at wall-buy; an op in creative who uses a wall-buy outside a game gets its settings screen instead, `client/settings/WallBuyScreen`): `/hs wallbuy set <cost> held` (a TacZ gun in your main hand becomes a `tacz_gun` spec, keeping unlimited ammo; any other item becomes a plain spec with its count and NBT), `/hs wallbuy set <cost> gun <gunId>`, `/hs wallbuy ammo <cost> [upgradedCost]`, `/hs wallbuy repurchase <refill_ammo|top_up|duplicate|deny>`, `/hs wallbuy power <true|false>`, and `/hs wallbuy describe`. They work on a perk machine too. `set` resets `repurchase` to the item kind's default and the ammo costs to their defaults, and keeps the power setting. `refill_ammo` is refused for non-guns. A `refill_ammo` config on a plain item (hand-edited NBT) acts as `top_up`.
- **Owning.** The rule looks at the held stack first, then the rest of the inventory and the offhand. TOP_UP counts every matching stack and adds the missing count. A full stack is refused and nothing is charged. An upgraded gun is one with `pap.tier > 0`.
- **Full guns** (2026-10-24). `WallBuy.Owned` carries the matched gun's `Ammo`: `ROOM`, `FULL` (`TaczBridge.ammoFull`: magazine full, a round chambered unless the gun is open-bolt, and any dummy reserve at its maximum), or `UNLIMITED` (the gun has unlimited ammo). `REFILL_AMMO` refuses a full gun with "That gun's ammo is full" and an unlimited one with "That gun has unlimited ammo", charging nothing; before, a refill charged the ammo cost however full the gun was. Since the prompt hides for a refusal, a full gun shows no prompt at its wall-buy until it has fired. A refill chambers a round. A gun fed by ammo items (no dummy reserve) gets only its magazine filled.
- **Refusals charge nothing.** Weapon slots, inventory room, the perk limit, and an uninstalled item are checked before charging; power is checked with the points. A gun at the slot limit replaces the gun in the selected hotbar slot. If the player isn't holding a gun, they get "Hold the gun you want to swap".
- **Prompt.** The client reads the local player's inventory and effects (`feature/Viewer`, set at client setup) and runs the same rule as the server, `WallBuyBlock.action`: a potion whose effects you all have is denied, and anything else goes through `decide`. So the prompt shows the item name with the price of what a use would do ("AK-47 [1000]" to buy, "AK-47 [250]" for ammo), "No power" while it needs power and the power is off, or nothing for a denied item.
- **Event.** `GameEvent.WallBuy(player, item, cost, refill)`, Lua `wall_buy` with `player`, `item` (the spec's id, such as `tacz:ak47`), `cost`, and `refill`. `GameEvents.emit` is now public, so features outside `game/` can emit events. A perk machine sells through the same `WallBuyBlock.sell` with its own receipt and emits `perk_bought` instead.
- **Vending machines** are wall-buys too: their block entity extends `WallBuyBlock.Entity`, with its own type id and defaults, and they share the settings screen and the commands. They sell through `WallBuyBlock.sale`, which `sell` wraps, so they can hand the item over later. See `perk-machines.md`.
- **Audit.** `PunchAudit.wallBuys` checks every wall-buy item in the map (built with the upgrader).
- **Off-Hand.** The slot limit is `players.weapon_slots` plus the player's `hellstorm:extra_weapon_slots` attribute, which the Off-Hand effect raises by 1.
- **Tests.** JUnit: `WallBuyTest` (every repurchase decision, full and unlimited guns, defaults, the codec, and `requires_power`), `WeaponSlotsTest`. GameTests in `WallBuyGameTests`: buying a gun (id, charge, event), a full gun refused free with no prompt, a refill at the ammo cost after emptying the gun that chambers a round and leaves it full, a refill at the upgraded ammo cost, an unlimited-ammo gun refused, a gun fed by ammo items getting only its magazine, TOP_UP golden apples, DENY, too few points, the slot limit swapping the held gun, a refusal when not holding a gun, and Off-Hand's extra slot. The potion rules and power are tested on the perk machine in `PerkGameTests`.

- **No commands** (2026-10-24). `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` are gone: each machine is set on its settings screen (an op in creative using it outside a game). Anyone else gets nothing from using it outside a game (until 2026-10-24 they saw its settings in chat). GameTest `WallBuyGameTests.usingAMachineOutsideAGameAsANonOpSaysNothing`. `CommandTreeGameTests` checks none of them is registered.

- **Plate size** (2026-10-29). The plate fits the item it sells: the box round everything the item draws, across the wall, with a 1/16 border, and never less than 0.75 by 0.75 of a block, centred on the item (`client/WallBuyPlate`, JUnit `WallBuyPlateTest`). An empty wall-buy has the 0.75 plate on the block's center. The size comes from drawing the item once into a `VertexConsumer` that keeps only where its vertices land, with the same pose and display context as the real draw, so it fits whatever the item's renderer draws. TacZ's models don't draw through the buffers they're given: `BedrockModel.render` draws into Minecraft's shared buffer source and then flushes it. So the first build measured nothing for a gun, and its flush ended the buffer the plate had already taken, which crashed the client the moment a wall-buy with a gun came into view ("BufferBuilder not started", 2026-10-29). Now the plate is measured before its buffer is taken, and a gun is measured through `tacz/GunModelCapture`: while it runs, two client mixins (`BedrockGunModelMixin`, `BedrockModelMixin`) draw the gun's own parts into the recorder and skip its attachments, laser beams and the accelerated path, so nothing shared is touched; outside a measurement they do nothing. Attachments aren't counted in the plate's size. It is measured again only when the item changes, and not kept while nothing is drawn (a model still loading). The plate is drawn by the renderer, a block of planks at a time so the texture never stretches, so the block model is empty but for its particle texture; the item model keeps the old full 16×16 plate. A long gun's plate reaches past the block, so the block entity's render box is the block grown by 1. The block's shape is still the full 16×16 plate: the server can't know how big a gun is drawn. Until then the plate was always the whole block's face.
- **Purchase defaults** (2026-10-25). Datapack files, loaded like gun balance (`data/PurchaseDefaults`, two `SimpleJsonResourceReloadListener`s, refreshed on `/reload`). A file's id is what it prices: `data/<ns>/hellstorm/gun_purchases/<path>.json` is the TacZ gun `<ns>:<path>`, and `item_purchases/` the same for item ids. Each file is a `data/PurchaseDefault` `{ cost, repurchase, ammo_cost?, pap_ammo_cost? }` (field names as in `WallBuy.Config`; the ammo costs default to half the cost and 4500), read strictly through `DataFiles`, so a bad file is skipped, logged and shown by the content audit; an item file with `refill_ammo` is refused. The mod ships one for every gun in TacZ 1.1.8's default pack (54, priced by type, `refill_ammo`, ammo half the price, upgraded ammo 4500) and the perk cans (Vigoraid 2500, Load-Up 3000, Saint's Brew 1500, Off-Hand 4000, Wandering Spirit 2000, Hellfire 2000, `deny`). First built the same day as a second SERVER config (`hellstorm-defaults.toml`), and moved because Forge's config spec only holds keys declared at startup: it deletes any key it doesn't know (`ForgeConfigSpec.correct`'s second step) and re-adds missing ones, so a section per gun couldn't be added or removed, and a list of one-line strings was hard to read. Power isn't in the files: it stays the machine's (vending machines need it, wall-buys don't). They apply in two places. The settings packet's `Held` carries the held item's defaults, and **Sell held** fills in cost, buying again and both ammo costs from them (an item with no file keeps the typed cost, as before). `WallBuyBlock.setPlacedBy` gives a hand-placed wall-buy its gun's defaults, unless the item carries a `BlockEntityTag` (a copied machine). Vending machines keep their own default (the Vigoraid can at 2500, a potion until 2026-10-28); potions have no files, since every potion is `minecraft:potion`. Tests: `PurchaseDefaultTest` (the codec's defaults and required fields, `refill_ammo` refused on items, a file for every default-pack gun and each can); `PacketRoundTripTest.settings` with defaults; GameTest `WallBuyGameTests.aPlacedWallBuyTakesItsGunsDefaultsAndEveryCanIsListed`.
