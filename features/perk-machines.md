# Perk machines

## Goal

Machines sell perks like Juggernog, Speed Cola, Double Tap, Quick Revive, Stamin-Up, and Mule Kick. A perk is a potion effect with no time limit. The machine sells the potion, and the player drinks it to get the perk. Perks need power by default and are lost when the player goes down.

## Data shape

- **Perk effects** are registered `MobEffect`s (`perk/PerkEffects`). Each is one attribute modifier, so anything else that changes the same attribute stacks with it.
- **A perk** is any effect in the mob effect tag `packapunch:perks` (`data/packapunch/tags/mob_effect/perks.json`). A datapack can add its own effects to the tag.
- **Potions.** One per perk effect, `packapunch:<id>`, giving the effect with `MobEffectInstance.INFINITE_DURATION`.
- **The machine** is a wall-buy (`wall-buys.md`): its block entity holds a `WallBuy.Config`.

```json
{ "item": { "item": "minecraft:potion", "nbt": { "Potion": "packapunch:juggernog" } },
  "cost": 2500, "repurchase": "deny", "requires_power": true }
```

## Implementation

- **Buying** goes through the wall-buy sale. It refuses without power when `requires_power` is set. It refuses only a potion whose effects the player already has; the perk limit isn't checked here. On success the player gets the potion. Emit `perk_bought`.
- **Drinking** is vanilla. A player in a game who would go past the perk limit is refused when they start drinking, so nothing is used up. Only drinking is limited: splash and lingering potions, and any other source of an effect, may take a player past it.
- **Losing perks.** Going `Downed` removes every beneficial effect. Quick Revive's solo self-revive is handled in `downs-and-revives.md`.
- **Names.** Effect and potion names are lang keys, so trademarked names can be renamed in a resource pack.

## Config surface

- Per machine: the same settings as a wall-buy (item, cost, repurchase, power).
- `players.perk_limit` (4).
- The `packapunch:perks` and `packapunch:self_revive` mob effect tags.

## Assets

- Machine block uses vanilla textures in a custom model, tinted with the color of the potion it sells.
- **Needed from you:** one HUD icon per default perk (16×16), and one mob effect icon per perk (18×18). Listed in `TEXTURES_NEEDED.md`.

## Depends on

`wall-buys.md`, `economy-and-points.md`, `power-switch.md`, `tacz-bridge.md`, `downs-and-revives.md`.

## Verification

- GameTest: buying a perk potion needs power, charges the cost, and emits `perk_bought`.
- GameTest: drinking Juggernog gives an endless effect that raises max health and heals; going down takes it.
- GameTest: at the perk limit a new perk can be bought but not drunk, and other sources still add it.
- GameTest: a machine refuses a potion whose effects the player already has.
- GameTest: a machine saved with the old `{ perk, cost }` settings loads as that perk's potion.

## Phase

5. Reworked to potions after phase 6.

## As built

- **Effects** (`perk/PerkEffects`, all `BENEFICIAL`, colors from the old perk JSONs). Each modifier has a fixed UUID.

  | Effect | Attribute | Modifier |
  |---|---|---|
  | `juggernog` | `minecraft:generic.max_health` | +20, `addition` |
  | `speed_cola` | `packapunch:reload_speed` | +1, `addition` |
  | `double_tap` | `packapunch:fire_rate` | +0.33, `addition` |
  | `quick_revive` | `packapunch:revive_speed` | +1, `addition` |
  | `mule_kick` | `packapunch:extra_weapon_slots` | +1, `addition` |
  | `stamin_up` | `minecraft:generic.movement_speed` | +0.07, `multiply_base` |

  - Gaining max health heals the gain, and losing it clamps health, as vanilla Health Boost does.
  - The amplifier scales the modifier as vanilla does, so a Juggernog II from a command adds 40.
  - `quick_revive` is also in the `packapunch:self_revive` tag, which is what gives the solo self-revive.
- **Attributes** (`registry/PapRegistries`). All four are syncable and added to every player.
  - `packapunch:reload_speed` (base 1, 0.1 to 10) and `packapunch:revive_speed` (base 1, 0.1 to 10) as before.
  - `packapunch:fire_rate` (base 1, 0.1 to 10) multiplies a TacZ gun's rounds per minute.
  - `packapunch:extra_weapon_slots` (base 0, 0 to 9) adds to `players.weapon_slots` (`game/WeaponSlots`).
  - The attributes are built outside their suppliers, because mob effects register before attributes and the effects need them.
- **Potions.** One per effect, so each effect also has splash, lingering, and tipped arrow forms, all named in the lang file (`item.minecraft.potion.effect.juggernog` is "Juggernog").
- **Effect icons.** No textures ship. `client/PerkEffectIcon` (`IClientMobEffectExtensions`) draws each perk's icon in the HUD and inventory as a square in the effect's color with the first letter of its name, so nothing draws as a missing texture.
- **What a perk is** (`perk/Perks`). An effect in `packapunch:perks`. `Perks.held` lists the ones a player has active, and `players.perk_limit` counts them.
  - `Perks.limitRefusal(held, adding, limit)` refuses a potion whose new perks would take the player past the limit: "You can only hold 4 perks". Perks the player already has and non-perk effects don't count. A potion of two new perks needs room for both.
  - Drinking: `LivingEntityUseItemEvent.Start` is cancelled on the server for a player in a game when the potion would go past the limit, with the usual refusal notice. The client keeps its drinking pose until the player lets go of use, since it doesn't know the limit. Splash and lingering potions and `/effect` aren't checked, on purpose. A machine sells a perk potion at the limit too (it only refuses an effect you already have), since the potion can wait in the inventory.
- **Losing buffs.** `Perks.clearBuffs(player)` removes every `BENEFICIAL` effect, perk or not. It runs on going down (`Downs.knockDown`), on dying in a game (`GameHooks.onDeath`), on `/pap leave`, on logout, and for every player when the game ends (`GameManager.end`). Harmful and neutral effects stay. Removing an effect undoes its attribute modifier, so Mule Kick's slot and Juggernog's health go with it.
- **Fire rate on TacZ guns** (`tacz/PerkGunEffects`). `AttachmentPropertyEvent` multiplies `ROUNDS_PER_MINUTE` by the holder's `packapunch:fire_rate`. The event only carries the stack, so the holder is the player (server thread) or the local player (client thread) whose main hand is that exact stack; game players are searched too, since test players aren't in the server's player list. It multiplies what is there, so Pack-a-Punch's change stacks.
  - Server: any perk effect with a fire rate modifier re-evaluates the held gun when it is added or removed.
  - Client: TacZ times shots on the client too. `tacz/LocalGunPerks` checks the local player's synced `fire_rate` each client tick and re-evaluates the held gun when it changes. This replaces `PerkSyncPacket`.
- **Reload speed and Speed Cola** (`tacz/ReloadTimes`, two mixins in `tacz/mixin`).
  - **Why this seam.** TacZ 1.1.8 has no reload-time property, but every reload asks one method how long ago it started: `ModernKineticGunScriptAPI.getReloadTime()`. That covers the built-in `defaultTickReload` (magazine guns like the AK) and every gun pack's Lua `tick_reload` (shell-by-shell guns like the M870 read `api:getReloadTime()` for each shell and for interruptions). Reloads tick only on the server (`LivingEntityMixin.onTickServerSide`), and the client learns the state through TacZ's synced reload state.
  - **Server.** `ModernKineticGunScriptAPIMixin` scales the answer at `RETURN` by the shooter's `packapunch:reload_speed`: `elapsed * speed`. Mobs without the attribute reload at normal speed.
  - **Client.** The client only plays the animation. `ObjectAnimationRunnerMixin` (a `client` mixin) speeds up `updateProgress` for animations whose name starts with `reload`, from both `update` and `updateSoundOnly`, so the hands and the sounds keyed to the animation keep pace with the server. Draw, inspect, and shooting animations are untouched.
- **Machine block** (`feature/PerkMachineBlock`). It keeps its own block, cabinet model, `FeatureKind.PERK_MACHINE`, and outline. Its block entity is `WallBuyBlock.Entity`, registered under its own type id `packapunch:perk_machine`, so machines saved before this change still load.
  - **Defaults.** A new machine sells a Juggernog potion for 2500, `repurchase: deny`, `requires_power: true` (`PerkMachineBlock.sells(potion, cost)`).
  - **Old saves.** The machine's codec also reads the old `{ perk, cost }` shape. It becomes the potion with the perk's id at that cost (2500 if none), `deny`, needing power. A perk id with no potion is logged and the machine keeps its default. Saving writes the wall-buy shape.
  - **Display.** `client/WallBuyRenderer` draws the sold item on the machine's front, over the glowstone panel, at half size. `client/PerkMachineColors` tints the body and label (`tintindex` 0) with `PotionUtils.getColor` for a potion and leaves anything else white. The block redraws when its block entity changes.
  - **Prompt.** "Juggernog [2500]", or "No power" while it needs power and the power is off. Hidden for a potion whose effects the player already has, from the local player's synced effects.
  - **Settings.** The wall-buy screen (title "Perk Machine"), with a "Needs power" row. `/pap wallbuy ...` works on it too, including `/pap wallbuy power <true|false>`.
  - **Event.** `GameEvent.PerkBought(player, item, cost)`, Lua `perk_bought` with `player`, `item` (the potion id for a potion, such as `packapunch:juggernog`; the item spec otherwise), and `cost`. A perk machine doesn't emit `wall_buy`.
- **HUD.** Perks show where every effect does, top right and in the inventory, with the icons from `client/PerkEffectIcon`. The bottom-left list of perk names was removed once that was enough.
- **Removed.** The datapack perks (`perk/PerkDef`, `PerkDefs`, `PerkEffect`, `data/packapunch/packapunch/perks/`), the `damage_multiplier` effect, `PlayerState.perks` and `bonusWeaponSlots`, `Game.givePerk` and `takePerks`, `PerkSyncPacket`, `client/ClientPerks`, `/pap perk`, and the perk machine's own settings screen.
- **Tests.**
  - JUnit: `PerksTest` (the limit rule), `WallBuyTest.requiresPowerIsOffUnlessSetAndRoundTrips`, `DownsTest` (solo self-revive through `GameWorld.canSelfRevive`; the fake world loses it on going down), `LuaEventsTest.aPerkPurchaseCarriesTheItemAndCost`, `ReloadAnimationTest`.
  - GameTests in `PerkGameTests`:
    - a new machine sells its Juggernog only with the power on, for 2500, with a `perk_bought` event and no `wall_buy`
    - drinking Juggernog gives an endless effect, 40 max health, and heals 20, all gone when the game ends
    - going down takes Juggernog, Mule Kick, and Resistance but not Weakness; dying takes Stamin-Up
    - at the limit, the machine charges nothing and sells nothing, and drinking a new perk is refused without using the potion; an already-held perk and Swiftness can be drunk
    - a machine refuses Juggernog to a player who has it, and its action is the denial the prompt hides on
    - Quick Revive gives self-revive and revive speed 2
    - old `{ perk, cost }` settings load as the potion, with and without a cost, and an unknown perk keeps what was there
    - Double Tap raises the held AK's cached RPM by 1.33 when the effect is added, and the game ending restores it
    - Speed Cola, bought and drunk, makes TacZ's own `reload()` and `tickReload` feed an empty AK in three quarters of the time; a plain `multiply_base` reload speed modifier does the same
