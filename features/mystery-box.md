# Mystery Crate

## Goal

Players pay to roll a random item from a weighted pool. The box moves to another location after some uses.

## Data shape

`data/<ns>/hellstorm/box_pools/<id>.json`:

```json
{
  "include_all_tacz_guns": true,
  "exclude_wall_buys": true,
  "entries": [
    { "item": { "tacz_gun": "tacz:m249" }, "weight": 2 },
    { "item": { "item": "minecraft:totem_of_undying" }, "weight": 1 }
  ],
  "overrides": { "tacz:glock_17": { "weight": 0 } }
}
```

`MysteryBox` BlockEntity: `{ poolId, cost, isActiveLocation, usesSinceMove }`. A map has several box locations. One is active at a time.

## Implementation

- **Pool build.** At game start, build the pool. If `include_all_tacz_guns`, add every gun from `TaczBridge.allGuns()` at weight 1. Remove the map's wall-buy guns if `exclude_wall_buys`. Add `entries`, then apply `overrides` (weight 0 removes).
- **Roll** through `Purchasable`:
  1. Charge `cost` (or the Fire Sale cost).
  2. Animate for `box.roll_ticks`, cycling item displays above the box.
  3. Pick from the pool with the game's seeded random, excluding items the player already holds.
  4. Show the result for `box.collect_ticks`. Only the roller can take it.
  5. Emit `mystery_box_opened` with the result.
- **Moving.** After `box.min_uses_before_move` uses, each roll has `box.move_chance` of producing the "move" result instead: refund the cost, mark this location inactive, pick a random other location, emit `mystery_box_moved`. Fire Sale makes every location active temporarily.
- **Weapon slots.** Taking a TacZ gun follows the same slot rule as wall-buys.

## Config surface

- `box.cost` (950), `box.fire_sale_cost` (10).
- `box.roll_ticks` (80), `box.collect_ticks` (240).
- `box.min_uses_before_move` (4), `box.move_chance` (0.15).
- Per box: pool id.

## Assets

- The box block uses vanilla textures in a custom model (for example `minecraft:block/chest` side parts or `minecraft:block/barrel_side`).
- **Needed from you** if you want a distinct look: a box texture. Listed in `NEEDED/TEXTURES.md` as optional.

## Depends on

`economy-and-points.md`, `wall-buys.md` (shared `ItemSpec` and slot rule), `tacz-bridge.md`, `power-ups.md` (Fire Sale).

## Verification

- JUnit: pool build with `include_all_tacz_guns`, `exclude_wall_buys`, entries, and a weight-0 override produces exactly the expected weighted set.
- JUnit: with a fixed seed, 10,000 rolls match the weights within tolerance.
- GameTest: a move refunds the cost and leaves exactly one active location.

## Phase

5.

## As built (phase 5)

- **Classes.** `data/BoxPool` (codec plus the pure `build` and `pick`), `data/BoxPools` (reload listener for `data/<ns>/hellstorm/box_pools/*.json`), `feature/MysteryBoxBlock` (block, block entity, and ticker), and `feature/MysteryBoxes` (per-game state: the box's current location, uses since the last move, built pools, and Fire Sale). `client/MysteryBoxRenderer` draws the item.
- **Shipped pool.** `hellstorm:default` is `{ include_all_tacz_guns: true, exclude_wall_buys: true }`. If no datapack defines it, the same pool is built in. Overrides are keyed by gun id or item id. An entry for an item already in the pool replaces it rather than adding a second copy; guns compare by id, other items by the whole spec.
- **Block.** Two blocks wide, like the upgrader (`feature/TwoBlockMachine`): placed facing you with its long side across your view, running toward the side of the clicked block you aimed at; either half rolls, and breaking one half takes the other. The `box` blockstate is `stored` at every location outside a game, `here` where the box is this game, and `away` at the others. See "As built (crate model)" below. Per-location config is `{ pool, cost? }`, set with `/hs box pool <id>` and `/hs box cost <points|default>`, and shown with `/hs box describe`.
- **Where the box starts.** One line in `GameManager.register` calls `MysteryBoxes.start`, which picks a random location with the level's random. It runs when the game is registered (the lobby opens), not at `game_start`, so players can see the box during the lobby and countdown. They can't roll it until the game starts: `Game.charge` refuses a waiting player ("Wait for the game to start"). GameTests also go through `register` without ticking a countdown. `MapReset` puts every box away (idle and inactive) when the lobby opens and when the game ends.
- **Pools are built on a game's first roll** of each pool id, and cached for the rest of that game, rather than at game start. They see the map's wall-buys, found through `features(WALL_BUY)` and their block entities, as configured when play begins.
- **Rolling.** The roll is charged through `Shop`. The block entity then stores `until`, in level game ticks, and a reel of up to 12 sample stacks from the pool, synced to clients. The ticker ends the roll at `until`. If the box doesn't move, it picks with the level's random, skipping items the roller already holds. Guns match by id. The offer lasts `box.collect_ticks`. Only the roller can take it, through the weapon-slot rule. Others get "That's someone else's". If nothing new is left for the roller, the roll is refunded. `mystery_box_opened` (Lua: `player`, `item`, `x`, `y`, `z`) fires when the result appears.
- **Its name in text** (2026-10-24). Everything players and map makers read calls it the Mystery Crate, never a box: the `/hs box` command titles ("Unable to set Mystery Crate pool", "Unable to set Mystery Crate cost", "Unable to describe Mystery Crate"), the settings check ("No Mystery Crate pool ... is loaded"), the `[box]` comments in `hellstorm-server.toml`, and the log lines (`Loaded N Hellstorm Mystery Crate pools`). The ids keep `box` (`/hs box`, `box_pools/`, `[box]`, `message.hellstorm.box.*`). A crate whose pool isn't loaded refuses players with `message.hellstorm.box.empty` ("The Mystery Crate is empty") instead of naming the pool, and logs a warning with the crate's position and the pool id for the server owner. `MachineNamesLintTest.noTextCallsTheCrateABox` fails the build on "mystery box" or "box pool" in a string (outside GameTests), and on "box" in a config comment or the lang file.
- **Moving.** The move check runs at the end of a roll: `usesSinceMove >= box.min_uses_before_move` and a roll under `box.move_chance`. A box never moves from a map's only location or during a Fire Sale. On a move, the roller is refunded, the old location goes idle and inactive, a random other location opens, the use count resets, players see "The Mystery Crate moved", and `mystery_box_moved` fires (Lua: `x`, `y`, `z` of the old location plus `to = {x, y, z}`). There's no teddy-bear animation.
- **Prices.** The server writes each open location's current price into the block entity for the prompt: the location's `cost`, else `box.cost`, or `box.fire_sale_cost` during a Fire Sale.
- **Fire Sale API.** `MysteryBoxes.setFireSale(game, on)` and `fireSale(game)`. On opens every location at the Fire Sale price. Off closes every location except the box's own once it is idle. A location still offering closes after it's collected or the offer expires. The power-ups phase only has to call it.
- **Renderer.** Replaced. See "As built (crate model)" below.
- **Tests.** JUnit `BoxPoolTest`: the build with every option produces the exact weighted list, the shipped default pool, codec errors, 10,000 seeded rolls within 6% of the weights, skipping held items, and the move threshold. GameTests in `MysteryBoxGameTests`: refusals charge nothing (too few points, busy box), a roll charges `box.cost` and is still rolling halfway through. After `roll_ticks`, the result is offered and refused to another player, and the roller takes it for free. A forced move refunds and leaves exactly one active location, with the event. The Fire Sale opens and closes locations. Ending the game puts every box away.

## As built (crate model)

- **Model.** Your Blockbench model, texture and `box_open` animation, as `geo/block/mystery_crate.geo.json`, `textures/block/mystery_crate.png` and `animations/block/mystery_crate.animation.json`, drawn by GeckoLib. The texture is your oak layout, 128 by 128 (`texture_width` 128), recolored as charred wood with the orange skull left as it was. The geometry's `format_version` is set to 1.12.0, which GeckoLib reads. The model is made with its body's corner at the origin, so the renderer and the item move it 7 pixels in x and z (`MysteryBoxBlock.MODEL_OFFSET`) to center it on its two blocks. If you re-center it in Blockbench, set that offset to zero. Its lid falls open toward the model's +x side, so its front, the side the open crate faces, is -x, the reverse of the upgrader's; `MysteryBoxRenderer` draws it turned half a turn about the seam (`BlockModelRenderer`'s `frontOnMinusX`) so it opens toward the player who placed it.
- **Block.** Oak plank sounds (`SoundType.WOOD`). Breaking it shows oak plank particles: the block model is only `particle: minecraft:block/oak_planks`. The crate's own texture is mostly empty space around its 59 by 32 pixel layout, and particles are cut from random parts of it, so most would be invisible. Both halves are 14 pixels tall.
- **Where the box isn't.** During a game, a location without the box is `away`: nothing is drawn and both halves have no shape, so players walk through. Whenever a crate that was showing goes `away` (the box moves, a Fire Sale location closes, or the lobby opens and the box picks its first location), it bursts into smoke (`poof` and `large_smoke` particles) with the fire-extinguish sound. Ending the game puts every location back to `stored`, closed, for map makers.
- **A roll** (`roll_ticks`, unchanged in length): the lid opens over its first `MysteryBoxBlock.LID_TICKS` (20 ticks). The client fits your 3.4-second `box_open` to that length. Then the guns rise from inside the crate, under the portal, to 1.35 blocks up, flicking from one to the next and slowing down until the last one settles. They stand upright, their length along the crate's, and don't turn.
- **Timing the rise on each client** (`feature/BoxSpin`). The server sends a roll once, when it starts, and the update tag carries `elapsed`, how many ticks into the roll the server is as it sends it. From there each client counts its own ticks, so which gun shows (`BoxSpin.rise` and `flick`, pure) depends only on that client's clock. It used to be worked out from the client's game time, which the server resets once a second (`ClientboundSetTimePacket`, every 20 server ticks); behind a busy server or a jittery connection that clock jumped back several ticks each second, and the guns went back with it, a stutter only multiplayer showed. The same roll sent again (its price changed) doesn't reset the clock. A player who comes into range mid-roll gets `elapsed` with the chunk and joins partway up. If the server runs behind, a client reaches the top first and holds the last gun until the offer arrives.
- **The portal.** Vanilla's animated `block/nether_portal` texture lies flat across the opening inside the lid's frame: 12 by 28 pixels, at 13 pixels up, half a pixel below the top of the frame. It is tiled a block per tile from the opening's corner, with the far tiles cut to fit. It is translucent and lit by itself.
- **The offer** hangs still at the top for `collect_ticks`. Taking it closes the lid: phase `closing` for `LID_TICKS`, playing `box_open` backward, then idle. If nobody takes it, the gun bursts into smoke and the lid closes. A roll with nothing new for the roller does the same, after the refund. When the box moves, the gun bursts into smoke and then the crate does, without closing. A closing crate can't be rolled.
- **Smouldering.** Every 5 seconds (`SMOULDER_TICKS`), each crate that's showing puts one or two vanilla `small_flame` particles on its outside, which drift up a little and burn out. The client's tick does it, at a random point weighted by area over the long sides, the ends, and the lid's top while the lid is shut. Each crate's timing is offset by its position, so crates don't flicker in step.
- **Sounds.** The lid creaks open with vanilla's chest-open sound and creaks shut with chest-close, each slowed to last the lid's `LID_TICKS` (pitch is playback speed: 0.515 s and about 0.625 s long at normal speed, so pitch 0.515 and 0.625 for a 1-second lid). The client won't play slower than half speed, so a lid slower than about 1 second would outlast the open sound. Each time the rising gun changes, the client plays a note-block bell (pitch 1.5, quiet), from its own tick, so the dings keep time with the guns it draws (`MysteryBoxBlock.Entity.rise` is the one timeline for both). When the gun bursts into smoke, the ender dragon's roar (`ENDER_DRAGON_GROWL`) plays with the fire-extinguish sound. The roar is at volume 0.6. The dragon's own is at 2.5, which carries 40 blocks; at 1 or under a sound carries vanilla's 16 blocks and fades over them, so only players near the crate hear it.
- **Closing animation.** If the animation file has a `box_close`, it is used, fitted to `LID_TICKS`. Otherwise `client/Animations` builds it from `box_open` played backward: each keyframe runs from its end value to its start, under the mirror of its easing, which is exact for GeckoLib's eases.
- **Item.** Drawn with the same model (`GeoBlockItem`), half size in inventory slots so the two-block crate fits one.
- **Tests.** `MysteryBoxGameTests` rolls from the head and checks that only the foot holds the block entity. Taking the result closes the lid, a closing box charges nothing, and it is idle after `LID_TICKS`. An offer nobody takes closes the lid. A moved-from location is `away` on both halves with no collision shape. After the game, every location is `stored` and solid again. Placing a crate from its item, looking north: aiming at the left half of the floor block puts the head to the left, aiming right puts the foot to the right, and with the left blocked it goes right; it always faces south, toward the player. A client copy loaded from the update tag halfway through a roll has its guns where the server's are. JUnit `BoxSpinTest`: the guns rise in order, every flick shows, and the last settles as the roll ends; the same roll sent again, behind or ahead of the client, changes no frame; a mid-roll arrival starts where the server is.
- **Check in game** (not covered by tests): the lid's swing at its sped-up pace, the portal's look and tiling, the guns' height and how they stand, the smoke, the sounds, the flames, and the item in a slot.

- **No commands** (2026-10-24). `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` are gone: each machine is set on its settings screen (an op in creative using it outside a game). Anyone else gets nothing from using it outside a game (until 2026-10-24 they saw its settings in chat). GameTest `WallBuyGameTests.usingAMachineOutsideAGameAsANonOpSaysNothing`. `CommandTreeGameTests` checks none of them is registered.
