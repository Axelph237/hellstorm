# Mystery Box

## Goal

Players pay to roll a random item from a weighted pool. The box moves to another location after some uses.

## Data shape

`data/<ns>/packapunch/box_pools/<id>.json`:

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
- **Needed from you** if you want a distinct look: a box texture. Listed in `TEXTURES_NEEDED.md` as optional.

## Depends on

`economy-and-points.md`, `wall-buys.md` (shared `ItemSpec` and slot rule), `tacz-bridge.md`, `power-ups.md` (Fire Sale).

## Verification

- JUnit: pool build with `include_all_tacz_guns`, `exclude_wall_buys`, entries, and a weight-0 override produces exactly the expected weighted set.
- JUnit: with a fixed seed, 10,000 rolls match the weights within tolerance.
- GameTest: a move refunds the cost and leaves exactly one active location.

## Phase

5.

## As built (phase 5)

- **Classes.** `data/BoxPool` (codec plus the pure `build` and `pick`), `data/BoxPools` (reload listener for `data/<ns>/packapunch/box_pools/*.json`), `feature/MysteryBoxBlock` (block, block entity, and ticker), and `feature/MysteryBoxes` (per-game state: the box's current location, uses since the last move, built pools, and Fire Sale). `client/MysteryBoxRenderer` draws the item.
- **Shipped pool.** `packapunch:default` is `{ include_all_tacz_guns: true, exclude_wall_buys: true }`. If no datapack defines it, the same pool is built in. Overrides are keyed by gun id or item id. An entry for an item already in the pool replaces it rather than adding a second copy; guns compare by id, other items by the whole spec.
- **Block.** A single block, 12/16 tall, using vanilla barrel textures. The `active` blockstate shows the open lid (`barrel_top_open`) where the box is and the closed lid (`barrel_top`) at empty locations. Per-location config is `{ pool, cost? }`, set with `/pap box pool <id>` and `/pap box cost <points|default>`, and shown with `/pap box describe`.
- **Where the box starts.** One line in `GameManager.register` calls `MysteryBoxes.start`, which picks a random location with the level's random. It runs when the game is registered (the lobby opens), not at `game_start`, so players can see and use the box during the lobby and countdown. GameTests also go through `register` without ticking a countdown. `MapReset` puts every box away (idle and inactive) when the lobby opens and when the game ends.
- **Pools are built on a game's first roll** of each pool id, and cached for the rest of that game, rather than at game start. They see the map's wall-buys, found through `features(WALL_BUY)` and their block entities, as configured when play begins.
- **Rolling.** The roll is charged through `Shop`. The block entity then stores `until`, in level game ticks, and a reel of up to 12 sample stacks from the pool, synced to clients. The ticker ends the roll at `until`. If the box doesn't move, it picks with the level's random, skipping items the roller already holds. Guns match by id. The offer lasts `box.collect_ticks`. Only the roller can take it, through the weapon-slot rule. Others get "That's someone else's". If nothing new is left for the roller, the roll is refunded. `mystery_box_opened` (Lua: `player`, `item`, `x`, `y`, `z`) fires when the result appears.
- **Moving.** The move check runs at the end of a roll: `usesSinceMove >= box.min_uses_before_move` and a roll under `box.move_chance`. A box never moves from a map's only location or during a Fire Sale. On a move, the roller is refunded, the old location goes idle and inactive, a random other location opens, the use count resets, players see "The Mystery Box moved", and `mystery_box_moved` fires (Lua: `x`, `y`, `z` of the old location plus `to = {x, y, z}`). There's no teddy-bear animation.
- **Prices.** The server writes each open location's current price into the block entity for the prompt: the location's `cost`, else `box.cost`, or `box.fire_sale_cost` during a Fire Sale.
- **Fire Sale API.** `MysteryBoxes.setFireSale(game, on)` and `fireSale(game)`. On opens every location at the Fire Sale price. Off closes every location except the box's own once it is idle. A location still offering closes after it's collected or the offer expires. The power-ups phase only has to call it.
- **Renderer.** While rolling, the reel flicks by and slows down (ease-out) as the item rises from 0.8 to 1.3 blocks. The result hangs and spins, then sinks back into the box during the last quarter of the collect time. Animations use the client's game time and the synced `until`, so the server syncs only when the phase changes.
- **Tests.** JUnit `BoxPoolTest`: the build with every option produces the exact weighted list, the shipped default pool, codec errors, 10,000 seeded rolls within 6% of the weights, skipping held items, and the move threshold. GameTests in `MysteryBoxGameTests`: refusals charge nothing (too few points, busy box), a roll charges `box.cost` and is still rolling halfway through. After `roll_ticks`, the result is offered and refused to another player, and the roller takes it for free. A forced move refunds and leaves exactly one active location, with the event. The Fire Sale opens and closes locations. Ending the game puts every box away.
