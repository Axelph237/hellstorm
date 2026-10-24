# Hellstorm

Hellstorm is a Minecraft mod for round-based zombie survival, in the style of Call of Duty: Zombies. The guns come from [TacZ](https://modrinth.com/mod/timeless-and-classics-zero). Map makers build an arena by hand out of rooms, doors, windows, wall-buys, a Mystery Crate, vending machines, the upgrader, traps and a power switch. Players then fight waves of zombies, earn points, buy their way through the map, and revive each other. Co-op works on dedicated servers, and any mob can be set up as a zombie, including mobs from other mods.

## Requirements

- Minecraft 1.20.1
- Forge 47.x. NeoForge for 1.20.1 also works.
- TacZ (Timeless and Classics Zero) 1.1.8 or newer, on both the client and the server

GeckoLib and LuaJ are bundled in the jar, so you don't install them.

## Quick start for map makers

All `/hs` commands below need operator permission, except `/hs game join`, `leave` and `status`, and `/hs game start` for players already in a game.

1. Create a world with the **Zombies Builder** world type, a void world, or use any world. Get the tools from the **Hellstorm Map Tools** creative tab.
2. Mark the rooms. Hold the Zone Wand and right-click two corners, then run `/hs zones add room <room>`. The corners are the blocks you look at. Each further pair of corners adds another box to the selection, merged with one it lines up with, so an L-shaped room is two pairs of clicks and one command. An add or cut uses the selection up and clears it. Sneak-right-click or `/hs zones cancel` clears it without using it. `/hs zones cut` takes the selection back out of every room and door it touches. Or stand inside a closed room and run `/hs zones detect <room>`, check the preview, then run `/hs zones confirm`. Make at least one room a start room, with `/hs zones add room <room> true` when you create it or `/hs zones set room <room> start true` later. `/hs undo` and `/hs redo` step back and forward through your `/hs zones` edits.
3. Place Zombie Spawn Markers inside the rooms.
4. Add doors and windows. Select the door's blocks with the Zone Wand and run `/hs zones add door <id> [cost]`. Run it again with the same id to add more blocks to that door. Fill a hole in a room's wall with any blocks, select them, and run `/hs zones add window <id> [boards]`.
5. Place the machines from the creative tab: wall-buys, the Mystery Crate, vending machines, the upgrader, trap controllers and the power switch. In creative mode, use a machine outside a game to open its settings screen. Commands also configure the machine you are looking at, for example `/hs wallbuy set <cost> held`, `/hs wallbuy set <cost> gun <gunId>` or `/hs upgrader cost <points>`. To hide a trap controller in a wall, use it in creative with a full block in hand; sneak and use it with an empty hand to undo.
6. Run `/hs map validate` and fix what it reports. `/hs map paths` shows which rooms connect, and where.
7. Run `/hs map mode play`. Edit mode blocks games. Spawn markers show between games and hide while one runs.
8. Each player runs `/hs game join`. Then anyone in the game runs `/hs game start`. `/hs game status` shows the scores, `/hs game leave` leaves the game and gives back your inventory, and `/hs game stop` ends the game.

To share a map, run `/hs map export <name>`. This writes a map pack to `hellstorm/maps/` in the game folder. Another world loads it with `/hs map import <name> [<pos>]`.

Mob profiles, perks, power-ups, wave events and Mystery Crate pools are datapack JSON, and maps can add Lua scripts. The design notes for each feature are in [`features/`](features/), and `examples/datapacks/` has sample datapacks.

## Building from source

You need JDK 17.

```
./gradlew build
```

The mod jar is `build/libs/hellstorm-<version>.jar`. The `-slim` jar next to it has no bundled libraries, so don't ship it. `./gradlew test runGameTestServer` runs the unit tests and the in-game tests. The in-game tests aren't in either jar.

## License

Hellstorm's own code and assets are under the [BSD Zero Clause License](LICENSE) (0BSD). Two textures are excluded, because they are made from Minecraft's own textures. They belong to Mojang, are not relicensed, and are used under the [Minecraft EULA](https://www.minecraft.net/eula) and [Usage Guidelines](https://www.minecraft.net/usage-guidelines):

- `assets/hellstorm/textures/block/hellfire_upgrader.png` contains copies of Minecraft's stone and enchanting table textures.
- `assets/hellstorm/textures/block/mystery_crate.png` is Minecraft's oak planks, darkened, with embers added.

The sounds in `assets/hellstorm/sounds/` (the cha-ching, the vending machine's, and the stand-ins in `mystery_box/`, `power/`, `debris/`, `music/`, `upgrader/`, `player/` and `ambient/`) are cut from free recordings in Pixabay's sound library. They are excluded too, and are used under the [Pixabay Content License](https://pixabay.com/service/license-summary/), which doesn't allow distributing them on their own.

The Minecraft sounds the game also plays are named, not included.

The bundled libraries are MIT licensed. Their notices are in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES), and the jar has a copy at `META-INF/THIRD_PARTY_NOTICES`. TacZ is a separate mod under its own licenses (GPL-3.0 for code, CC BY-NC-ND 4.0 for assets), and Hellstorm doesn't include any of it.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
