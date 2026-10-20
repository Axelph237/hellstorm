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

## As built (phase 5)

- **Data.** `perk/PerkDef` and `perk/PerkEffect` (codec dispatch on `type`), loaded by `perk/PerkDefs` from `data/<ns>/packapunch/perks/`. `requires_power` defaults to true and `color` to white. A perk whose `attribute_modifier` names an unknown attribute is logged and skipped. Defaults ship under `data/packapunch/packapunch/perks/`: Juggernog 2500 (+20 max health), Speed Cola 3000 (`reload_speed` 0.5), Double Tap 2000 (`rpm_multiplier` 1.33), Stamin-Up 2000 (+7% movement speed, `multiply_base`), Quick Revive 1500 (`revive_speed` 0.5 and `self_revive`, no power needed), Mule Kick 4000 (`weapon_slots` 1). Names are `perk.packapunch.<id>` lang keys.
- **Ledger in `PlayerState.perks`,** a map from perk id to the `PerkDef` as it was when bought. Removal undoes exactly what was applied, even after a `/reload` changes or deletes the perk mid-game. `Game.givePerk` records a perk and emits `GameEvent.PerkBought` (`perk_bought`, with `player` and `perk`); `Game.takePerks` clears them. `perk/Perks` applies and removes effects on the `ServerPlayer`.
- **Buying** goes through `Shop` (the design's `Purchasable`). `Sale.check` refuses a perk the player has or a full list (`players.perk_limit`); `Game.charge` refuses without power.
- **Effects.**
  - `attribute_modifier`: a transient modifier with a UUID from the perk id and effect index (`PerkDef.modifierId`). Transient, so it is never saved with a player who disconnects mid-game. Gaining max health heals the gain; losing it clamps health.
  - `damage_multiplier`: multiplies `EntityHurtByGunEvent.Pre`'s base amount for the attacking player (`tacz/PerkGunEffects`).
  - `rpm_multiplier`: multiplies `ROUNDS_PER_MINUTE` in `AttachmentPropertyEvent`. The event only carries the stack, so the holder is found as the game player (server thread) or local player (client thread) whose main hand is that exact stack, which is how TacZ evaluates guns. TacZ times shots on the client too, so the client gets its multiplier through `PerkSyncPacket`. Buying or losing the perk re-evaluates the held gun on both sides (`AttachmentPropertyManager.postChangeEvent`). Both multiply what is there, so Pack-a-Punch's changes stack.
  - `weapon_slots`: adds to `PlayerState.bonusWeaponSlots` and subtracts on removal.
  - `reload_speed`: parsed, no effect; a one-time warning is logged when a loaded perk uses it. See below.
  - `revive_speed` and `self_revive`: parsed and stored with the perk for the downs feature; no effect yet.
- **Speed Cola finding (TacZ 1.1.8).** `GunProperties` has no reload time property, so `AttachmentPropertyEvent` can't change it. Reload times come from the gun's shared `GunData.getReloadData()` (`feed` and `cooldown`, `empty_time` and `tactical_time`), compared against `System.currentTimeMillis() - ShooterDataHolder.reloadTimestamp`, in `ModernKineticGunItem.tickReload` or the gun's Lua script on each side. The client runs its own reload (`LocalPlayerReload`) and animation from the same data. The only lever is shifting the public `ShooterDataHolder.reloadTimestamp` every tick, server side (what the script API's `adjustReloadTime` does). That would finish reloads early on the server while the client's animation plays at full length, so it isn't clean and isn't done.
- **Losing perks.** `Perks.removeAll(game, player)` runs when the player dies in a game (`GameHooks.onDeath`, before the player is set to spectate, so dying loses perks as going down will), on `/pap leave`, on logout, and for every player when the game ends (`GameManager.end`, using the game's player lookup so FakePlayers count too). Death in a game is cancelled, so the player entity is never recreated; without this the modifiers would carry over to the next round. `removeAll` is the hook for `Downed` in `downs-and-revives.md`. Losing Mule Kick lowers `bonusWeaponSlots`; dropping the extra gun is up to the wall-buy feature.
- **Machine block** (`feature/PerkMachineBlock`): a horizontal-facing block with `ConfigBlockEntity<Config { perk }>`. The model is a vanilla-textured cabinet (iron block trim, quartz pillar body, white concrete label, glowstone panel). The body and label have `tintindex` 0, colored by `client/PerkMachineColors` from the block entity.
- **Machine cost.** A machine's settings are `{ perk, cost }`. `cost` replaces the perk's own cost at that machine and is empty by default. Set it with `/pap perk cost <points|default>` or the settings screen. The prompt, the charge, and `describe` all use `Config.costOf(def)`.
- **Client data.** Clients have no datapack perks. The block entity's update tag adds a `display` compound (perk id, name key, color, cost, requires power), resolved from `PerkDefs` each time the server sends the machine, and never saved. After `/reload`, `OnDatapackSyncEvent` resends every loaded machine in each level's map. On receipt the client redraws the block so the tint updates. The prompt reads "Juggernog [2500]" and is hidden for a perk the player already holds.
- **HUD.** `PerkSyncPacket(badges, rpmMultiplier)` is sent whenever a player's perks change. `client/PerkHud` draws the perk names as colored text badges at the bottom left while in a game. The state clears on disconnect. The perk icons in `TEXTURES_NEEDED.md` would replace the badges.
- **Commands.** `/pap perk set <perk>` (the machine you're looking at, suggests loaded perks) and `/pap perk list`.
- **Tests.** JUnit: `PerkDefTest` (the shipped defaults, every effect type, round trips, invalid values), `PerksTest` (refusal rules, multipliers), `GameTest.perksAreRecordedWithAnEventAndTakenTogether`, `PacketRoundTripTest.perkSync`. GameTests in `PerkGameTests`: Juggernog raises max health and charges, a second purchase is refused and not charged, and ending the game removes the modifier; the perk limit refuses the fifth; a power-gated perk waits for power while Quick Revive doesn't; Mule Kick adds a slot and dying removes every perk; Double Tap raises the held AK's cached RPM by 1.33 and the game ending restores it.
