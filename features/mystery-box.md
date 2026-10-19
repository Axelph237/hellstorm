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
