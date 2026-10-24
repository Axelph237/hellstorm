# The upgrader (Dante's Toil)

## Goal

The upgrader, which players know as Dante's Toil, upgrades any item that is marked punchable. A datapack can say exactly what an item becomes. TacZ guns without a definition get a default upgrade. Anything else is an error that is caught at load time and refused safely at the machine.

## Data shape

Item NBT:

```json
{ "pap": { "punchable": true, "punch_id": "mymap:ray_gun_mk2" } }
```

- `punchable` (bool) marks the item as accepted by the machine.
- `punch_id` (optional) names a `PunchDef`.

`data/<ns>/hellstorm/punch/<any name>.json` (shape since 2026-10-23, see "Upgrade files" under As built):

```json
{
  "m4_tier2": {
    "item-id": "tacz:m4a1",
    "nbt": { "pap": { "punch_id": "mymap:m4_tier3" } },
    "name": "item.mymap.m4_upgraded",
    "cost": 5000,
    "carry-over": { "ammo": false, "attachments": true }
  },
  "defaults": {
    "pistol": { "nbt": { "pap": { "damage_multiplier": 3.0 } }, "method": "merge" }
  }
}
```

The mod ships defaults under the `hellstorm` namespace. A datapack overrides one by using the same pap id. A result can carry its own `pap` tag, which allows multi-tier upgrades.

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
3. On success, charge the cost, hold the item for `pap.process_ticks`, then offer the result for `pap.collect_ticks` to that player only. Emit `upgrader_used`.

**`PunchAudit`** reports the two `Rejected` errors before anyone plays:
- Runs on server start, after `/reload`, and inside `MapValidator` at game start.
- Scans every item source the mod controls: wall-buy BlockEntities (through the feature index), box pools, the starting loadout, and every `PunchDef` result.
- Each problem is an ERROR log line with its source and position, a chat message to ops on join, and a line in `/hs map validate`.
- Items from outside these sources (chests, other mods) can't be scanned. The machine still rejects them safely.

## Config surface

- `pap.default_cost` (5000), `pap.process_ticks` (60), `pap.collect_ticks` (240), `pap.max_tier` (1).
- TacZ multipliers in `tacz-bridge.md`.
- Per `PunchDef`: `item-id`, `nbt`, `name`, `cost`, `carry-over`.
- Per TacZ gun class: a default (`nbt` and `method`) applied after the built-in upgrade.

## Assets

- Machine block uses vanilla textures in a custom model.
- **Needed from you** if you want a distinct look: a machine texture. Optional in `TEXTURES_NEEDED.md`.

## Depends on

`tacz-bridge.md`, `wall-buys.md` (`ItemSpec`), `power-switch.md`, `economy-and-points.md`, `map-validation.md`.

## Verification

- JUnit, one test per outcome. For each `Rejected` case, the returned stack equals the input and the cost is 0.
- JUnit: `PunchAudit` flags a punchable non-TacZ item with no id and a punch id that doesn't exist, and does not flag a valid one.
- JUnit: a datapack def with the same id as a `hellstorm` default replaces it.
- In game: upgrade a non-gun wall-buy item with a `punch_id` and receive the result. Insert a punchable stick with no id: it comes back, no points are taken, and the load-time ERROR was in the log.

## Phase

5.

## As built (phase 5)

- **Code.** `pap/PunchDef`, `PunchDefs` (reload listener for `data/<ns>/hellstorm/punch/`), `PunchOutcome`, `PunchResolver`, and `PunchAudit`. The machine is `feature/PapMachineBlock`, drawn by `client/PapMachineRenderer`. The TacZ side is `tacz/PapGunStats` plus one mixin. `gametest/PapGameTests` covers the machine, and JUnit (`pap/*Test`) covers the resolver, codec, and audit.
- **Two blocks wide.** `PapMachineBlock` has `facing` and `part` (`foot`/`head`). `facing` is the way its front (the open side of the table, without the skull and candles) faces. It's placed facing you, its long side across your view: the other half goes beside the spot you place it, toward the side of the clicked block you aimed at, or the other way if there's no room (`TwoBlockMachine.getStateForPlacement`). The head is to the left of someone facing the front (`TwoBlockMachine.along`). The foot holds the block entity and is the position the map indexes. Using the head, from right click, the use key, or the settings screen, goes to the foot (`FeatureBlock.mainPos`). Breaking either half removes the other. The models are `pap_machine_foot` and `pap_machine_head` (vanilla obsidian, smithing table, and anvil textures), a 12-pixel table with a rail on its three outer edges.
- **The fire.** The item lies flat on top across both halves, along the machine, turned face up the way a floor item frame does it, at 0.58 scale and centered across the table. TacZ draws a gun about three blocks long in that view with its middle off center (measured on the AK-47 by recording its vertices), so other guns may sit slightly off.
- **The renderer was never registered** until this change: its nested `Registration.register(RegisterRenderers)` had the same generated listener name as the power-up renderer's, and Forge's event bus reuses listeners by name, so only the power-up one ran. It is now registered in `client/FeatureRenderers`, and `EventSubscriberLintTest` fails the build on any such clash. Paying throws an electric spark at the foot end. While the job runs, flames creep along the item from that end, with sparks at the front, in step with `pap.process_ticks` (the job carries its start time, synced to clients). When the fire reaches the far end, a burst of flame and a fire-charge sound replace the item with the upgraded one, which lies there until its owner takes it.
- **Tests.** `PapGameTests.eitherHalfSellsTheFireSpreadsAndBreakingOneHalfRemovesBoth`: the head sells and hands back the upgraded gun, the fire is about halfway along at half time, and breaking the head removes the foot and the map entry. The particles and how the item lies are checked in game.
- **The resolver is pure.** `resolve(tag, isTaczGun, defs, defaultCost, maxTier)` takes the NBT and a boolean, so JUnit covers every branch. `Replace` carries the def, not a built stack; the machine builds it. There is one more reason, `MAX_TIER`: a gun still marked punchable at or past the current `pap.max_tier`. A `punch_id` wins over the default gun upgrade and the tier cap. Every `Rejected` costs 0.
- **Costs.** The order is the def's `cost`, then the machine's `/hs upgrader cost <points|default>`, then `pap.default_cost`. The prompt shows the machine's cost. The client has no defs, so a def with its own cost isn't shown in the prompt.
- **Stats live on the stack.** The default upgrade adds 1 to `pap.tier` and multiplies `pap.damage_multiplier`, `pap.magazine_multiplier`, and `pap.rpm_multiplier` on the stack by the game's per-tier settings. The hooks read only the stack, so they work on both sides (the client predicts fire rate and draws the HUD) and outside a game. A datapack result can carry these keys too.
  - Damage: `EntityHurtByGunEvent.Pre` multiplies `getBaseAmount` for the attacker's held gun when it is the same gun as the event's.
  - RPM: `AttachmentPropertyEvent` multiplies the cached value.
  - Both multiply what is already there, so perks stack.
- **Magazine size uses a mixin.** TacZ's property cache has no capacity, as the phase 0 spike found, and an extended-mag attachment can't express a multiplier. So `tacz/mixin/AttachmentDataUtilsMixin` scales the return value of `AttachmentDataUtils.getAmmoCountWithAttachment`. Reloads, the HUD, and gun scripts all read that method. The mixin is unremapped, has no refmap, and targets only TacZ.
  - **`build.gradle` changed** for this: `--mixin.config` in the dev runs, and `MixinConfigs` in the jar manifest. Forge 47 has no mods.toml mixin entry.
  - The reserve (`MaxDummyAmmo`) is multiplied too, then the gun is refilled.
  - The name becomes `item.hellstorm.upgraded` ("Upgraded %s") around the gun's own name key, so a second tier doesn't read "Upgraded Upgraded".
  - `punchable` is removed once `pap.max_tier` is reached.
- **Machine flow.** Use with an item in the main hand. The machine resolves the item and builds the result before charging. On a refusal the item stays in hand and nothing is charged. Refusals: not punchable, unknown id, no upgrade path, max tier, a result item or gun that isn't installed, an empty hand, a busy machine, no power. On success the machine charges, takes one item, emits `upgrader_used` (`player`, `item` = gun id or item id), and holds the item for `pap.process_ticks` game ticks. Then it offers the result to the owner for `pap.collect_ticks`. Collecting goes through `Shop` at cost 0, into the empty main hand or else the inventory. Other players get "The machine is busy".
- **Uncollected results are lost,** as in CoD. The owner gets an action-bar message. Giving the item back would also need rules for a player who is downed, has left, or has a full inventory.
- **Prompt.** The prompt for collecting shows to everyone, because `Prompted.prompt()` doesn't know who is looking. Only the owner can collect.
- **State.** The job (owner, input, result, phase, deadline) is saved and synced with the block entity. `MapReset` empties every machine.
- **Starting guns are now punchable** (`Loadout`), like the CoD starting pistol. Starting items are bare ids, so they can't be punchable.
- **Audit.** `PunchAudit.SOURCES` is a list of `Source` functions (`ServerLevel -> List<Entry(source, pos, ItemSpec)>`). Today it holds every def result and the starting loadout.
  - It runs on `ServerStartedEvent` and on `OnDatapackSyncEvent` with no player (end of /reload), against the overworld, and logs each finding as an ERROR.
  - `GameManager.validationContext` passes the findings to `MapValidator` through a new `ValidationContext.itemProblems`. They are WARNINGs (`pap_unknown_punch_id`, `pap_no_upgrade_path`), so they show in `/hs map validate` but don't block a game, because the machine refuses those items safely.
  - Ops get a chat summary on joining when anything is flagged: the count, the first five findings with positions, and a pointer to `/hs map validate` (`PunchAudit.opNotice`).
- **Shipped default.** `hellstorm:iron_sword`: a diamond sword with Sharpness III, named "Upgraded Sword", for 2500. Try it with `/give @s iron_sword{pap:{punchable:1b,punch_id:"hellstorm:iron_sword"}}`.
- **Multi-tier defs through `tacz_gun`** (2026-10-23; unsupported until then). `ItemSpec.Gun` takes an `nbt` merged onto the built gun, so a gun result can carry its own `pap.punch_id`.
- **Upgrade files** (2026-10-23, replacing one file per def with `result`). A file in `data/<ns>/hellstorm/punch/` is an object of entries; its name no longer matters. `PunchDefs` reads them all into one `Loaded(defs, defaults, errors)`.
  - **Pap ids.** A key with a colon is the pap id as written (`"hellstorm:iron_sword"`); a key without one is in the file's namespace (`"iron_sword"` in `data/hellstorm/...` is `hellstorm:iron_sword`). So an item's `punch_id` names an entry the same way it named a file before, and the shipped `iron_sword.json` keeps its id.
  - **Entry.** `item-id` (required) and `nbt` (a JSON object or an SNBT string, `util/Codecs.COMPOUND`). Kept from the old shape, as optional extra keys: `name` (a lang key; the shipped sword uses it, and player-facing names come from lang keys), `cost` (the sword costs 2500), and `carry-over` (`ammo`, `attachments`), which multi-tier gun upgrades need to keep a player's attachments. Hyphenated to match `item-id`. Dropped: `count` (always 1) and `unlimited_ammo`.
  - **Gun results.** `PunchDef.result()` asks `TaczBridge.gunExists(item-id)` when the result is built, not when the file loads (TacZ's gun packs may load after us). A gun id builds an `ItemSpec.Gun`: full magazine and reserve, `nbt` merged on, and punchable unless `nbt` sets `pap.punchable` false. Anything else is the item with exactly `nbt`.
  - **`defaults`** is reserved: `"defaults": { "<gun class>": { "nbt": ..., "method": "merge" | "modify" | "remove" } }`. A pap id named `defaults` must be written with its namespace. The class is the TacZ gun index's `type` (`TaczBridge.GunInfo.type`): TacZ ships `pistol`, `smg`, `rifle`, `shotgun`, `sniper`, `mg` and `rpg`, and a gun pack can add more. `PapGunStats.upgrade` runs the built-in upgrade (tier, multipliers, reserve, refill, name), then applies the class's `GunDefault` to the gun's whole NBT. `method` defaults to `modify`; `nbt` defaults to empty.
    - `merge`: `CompoundTag.merge`, adding and overwriting keys, into compounds on both sides.
    - `modify`: overwrites only keys the gun already has, into compounds on both sides; adds nothing. A list is replaced whole.
    - `remove`: removes the given keys; a non-empty compound removes keys inside it, any other value (`{}` included) removes the key. Values are ignored.
    - These three readings are an interpretation of the request, not yet confirmed by the user.
  - **Overrides.** Files load lowest-priority datapack first (by `Resource.sourcePackId` against `listPacks()`), then by file id. A later file's pap id or gun class replaces an earlier one's, so a world datapack overrides the mod's. Replacing a file by its path still replaces the whole file.
  - **Bad entries** are skipped one by one; the rest of the file loads. Each is kept in `Loaded.errors` with its file and key, and not logged at load: `PunchAudit` reports it.
  - **Audit.** `PunchAudit.Finding` is now `(kind, message, pos)`, its `Kind` holding the problem code. Besides the two item findings it reports, through `checkFiles`: `pap_bad_entry` (a skipped entry: bad JSON, a bad pap id, a bad method, bad NBT, a missing `item-id`), `pap_missing_result` (an `item-id` that is neither an installed item nor a TacZ gun), and `pap_unknown_gun_class` (a `defaults` class no installed gun has; the message lists the real ones). All are warnings. The op notice reads "found N problems with upgrades". The audit also reads a gun result's `nbt`, so a gun upgrade whose `punch_id` names nothing is flagged.
  - **Tests.** JUnit: `PunchDefTest` (each field, NBT as object and SNBT, namespacing, the reserved key, overrides, bad entries), `GunDefaultTest` (each method on a sample gun's NBT, the codec's defaults and errors), `PunchAuditTest` (`checkFiles`). GameTest: `PapGameTests.aGunClassDefaultChangesTheUpgradedGunAndAGunResultIsAFullGun` runs each method on a real upgraded AK through `PapGunStats.upgrade`, checks a pistol default leaves it alone and that TacZ's classes are the ones above, and builds a gun result. The machine passing the loaded defaults is left to the in-game check, since a GameTest can't load a datapack without changing every other test's upgrades.
- **Model.** Vanilla textures only: a crying-obsidian base with a smithing-table top, and an anvil-textured frame. The item spins low inside the frame while it works, then rises and turns slowly once ready. No new textures. The optional texture is already listed in `TEXTURES_NEEDED.md`.

## The upgrader model and the fire (phase 7)

- **Name.** Players see it as Dante's Toil (`block.hellstorm.pap_machine` and `name.hellstorm.pack_a_punch` in the lang file); the registry ids and the code still call it `pap_machine`. Ops' text and logs call it the upgrader, and its command is `/hs upgrader cost`. It sounds like stone when placed or broken, and breaks into stone particles.
- **Wording.** Players and ops read "upgrade" and "the upgrader", never Pack-a-Punch: the shipped sword is the "Upgraded Sword", the settings screen is titled with the machine's lang name, the audit and its log lines say "upgrade definition" and "the upgrader", and the Lua event is `upgrader_used`. The old name stays only where renaming would break saved worlds or datapacks: the `pap_machine` block and item id, the `pap` item NBT compound (`punchable`, `punch_id`, `tier`, the multipliers), the `data/<ns>/hellstorm/punch/` folder, the `pap.*` server-config keys, the wall-buy's `pap_ammo_cost` setting, and the lang keys.

- **Model.** Your Blockbench model `hellfire_upgrader` (a table with a cloth, a skull at the foot end, and three lit candles at the head end), drawn by GeckoLib so its free rotations (the skull at 20 degrees, the candles at 27.5, the wicks at 17.5 and 72.5) stay as you made them. GeckoLib is bundled in the jar.
  - The geometry is `geo/block/hellfire_upgrader.geo.json`; its bones `bone` and `candle_three_candles_lit` were renamed `skull` and `candles`. The foot's block entity draws the whole table, from the foot toward the head along +z with its front on +x, turned so the head is `TwoBlockMachine.along` and the front faces `facing` (`client/BlockModelRenderer`, shared with the Mystery Crate). The two-block placement, either half selling, and one half breaking the other are `feature/TwoBlockMachine`, also shared. Both halves' block models are only a particle texture, and both are 13 pixels tall.
  - GeckoLib draws a model with one texture, so `client/BoneTextures` gives the `candles` bone vanilla's `candle_lit` texture and the `skull` bone the skeleton's; everything else uses `textures/block/hellfire_upgrader.png`. The UVs carry over unchanged because GeckoLib scales them by the model's texture size (64), not the image's.
  - The item is a flat item with your 16-pixel texture, `textures/item/upgrader.png` (`models/item/pap_machine.json`, `item/generated`), not the block's model.
- **Candles** burn like vanilla's: light level 9 (three candles' worth), a small flame at each wick, and now and then smoke and the candle crackle (`animateTick`, foot only, wick points turned with the model).
- **The gun** lies on the 13-pixel table top, 3 pixels toward the side without the skull and candles, so its length clears both.
- **The fire.**
  1. Using the machine lays the gun down (the smithing-table sound) and lights it with a fire-charge woosh.
  2. While it burns, columns of vanilla's animated fire sprites (`fire_0` and `fire_1`) engulf the gun: they rise from the table over half a second, flicker, face the camera, and light themselves, as a burning entity's flames do (`PapMachineRenderer.flames`). Embers and smoke rise off it.
  3. When `pap.process_ticks` are up, the fire goes out: the fire-extinguish sound and a burst of sparks and smoke, and the upgraded gun lies there.

  The flames' growth and flicker run on the machine's own client tick count (`PapMachineBlock.Entity.burning()`), not the client's game time, which jumps when the server lags (2026-10-24). When a burn reaches a client, its start is set back by how long the server says it has burned, so a player who walks up mid-burn sees it already grown.
  4. Half a second later a soft bell rings: it's ready.
- **Rendering both halves.** The block entity's render box covers both blocks and the flames above, so the gun and the fire don't vanish when only the head half is on screen.
- **Checked in a live client:** the GeckoLib model draws with all three textures and runs from the foot to the head for all four facings (vertices captured from the renderer: 16 by 32 pixels, 13 tall, the candles at the head end and the skull at the foot, both on the side away from the gun); the item draws with its floor at the bottom of the item's cube, 32 pixels long; the candle flames sit on the wick tops measured from the rendered model; the flames are 5 two-sided columns along the gun's line, from just under the table top to 1.54 blocks up; the candle flames land on the wicks for all four facings.
- **Fire sound** (2026-10-23). Putting an item in plays the anvil at pitch 0.6 (slowed) and the fire whoosh (`upgrader/fire`, stretched to the 3 s the item burns at the default `pap.process_ticks` of 60, raised to just under full scale, played at pitch 0.9), at the machine, in place of the smithing table and the fire charge. A changed `process_ticks` doesn't change the sound. See `SOUNDS_NEEDED.md`.

- **No commands** (2026-10-24). `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` are gone: each machine is set on its settings screen (an op in creative using it outside a game). Anyone else sees its settings in chat, ending "An op in creative changes it by using it". `CommandTreeGameTests` checks none of them is registered.
