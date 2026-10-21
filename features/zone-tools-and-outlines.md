

**`/hs map paths`** lists each zone connection and the cell the pathfinder uses for a normal 1x2 zombie: a doorway, or adjacent cells where zones touch. It also reports connections with no walkable crossing, and pairs joined only by a closed door.

**Debug rules** (`/hs debug [rule [true|false]]`, per world, saved like gamerules; `/hs debug` lists them):
- `show_paths` (default false). While you hold a map tool, the server sends your client every game zombie's current path (red lines) and the doorway cells the pathfinder uses (purple boxes) every half second. With no game running, the doorways still show, computed from the map and cached until it changes.
- `show_errors` (default true). Send script errors to ops' chat.
