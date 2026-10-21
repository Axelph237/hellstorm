

**`/hs map paths`** lists each zone connection and the cell the pathfinder uses for a normal 1x2 zombie: a doorway, or adjacent cells where zones touch. It also reports connections with no walkable crossing, and pairs joined only by a closed door.

**Debug rules** (`/hs debug [rule [true|false]]`, per world, saved like gamerules; `/hs debug` lists them):
- `show_paths` (default false). While you hold a map tool, the server sends your client every game zombie's current path (red lines) and the doorway cells the pathfinder uses (purple boxes) every half second. With no game running, the doorways still show, computed from the map and cached until it changes.
- `show_errors` (default true). Send script errors to ops' chat.
- `no_zombies` (default false). Games spawn no zombies: `ForgeGameWorld.spawnZombie` refuses, and the game treats that as a spawn that didn't happen. A round's zombies never come, so it doesn't end until the rule is off again. Zombies already up stay.

**The wand's model** is your Blockbench model, a cube frame with a smaller frame turned inside it, drawn by GeckoLib (`geo/item/zone_wand.geo.json`, `textures/item/zone_wand.png`) so the inner frame keeps its free rotations. Both frames turn about the middle, looping every 10 seconds (`animations/item/zone_wand.animation.json`, `zone_wand_spin`, played by `ZoneWandItem`'s controller), wherever the wand is drawn. `client/BlockAlignedItemRenderer` puts the model's floor where a block model's would be, so `models/item/zone_wand.json` uses vanilla's block display transforms.
