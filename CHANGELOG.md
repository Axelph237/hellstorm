# Changelog

## Unreleased

- Players in a game can't hurt each other, by gun, melee, or blast. Turn friendly fire on with `players.friendly_fire` in the server config. A player's own blast still hurts them.
- Zombies spawn only in rooms a player has walked into this game, and the start rooms. Buying a door no longer makes the rooms behind it spawn: someone has to go in. Once found, a room spawns for the rest of the game, wherever the players are, with markers nearer the players still picked more often. The `spawning.adjacent_hops` setting is gone.
- Repairing one window over and over stops paying: a player earns at most 100 points (ten boards) at any one window per round (`economy.repair_cap_per_window`). Boards still go back on past that, for nothing, until the player repairs a different window or the next round starts. The 500 per round cap across all windows stays.
- Zombies find their way with Minecraft's own pathfinding. Near a player they walk straight at them; farther off they're given a spot partway along the shortest walk, picked again as they go. Rooms and doorways no longer decide the route, so zombies cross from one room into the next without stalling at the boundary. They still go round open trapdoors and door jambs, and an unbought door is still a wall to them.
- `/hs map paths` lists one crossing cell for each pair of connected rooms, and `show_paths` draws every zombie's path.
- Hellhounds are half again the size of a wolf. Mob profiles take a `scale` that sizes the mob's hitbox and how it's drawn (`hellstorm_scale` in its NBT).
- Some zombies are runners (`rounds.runner_chance`, 10%), and the last zombie of a round always runs. A runner walks as fast as a walking player, 4.3 blocks a second, unless its round already makes it faster (zombies sprint about that fast from round 9), so the last zombie no longer outruns a sprinting player. A crawler can run too, at 0.8 of that.
- Game mobs drop nothing in a game, whichever mod they come from: no loot, equipment, or XP, and nothing another mod adds to their drops or spawns as they die. A mob a game mob leaves behind when it dies (a slime's young, a mod's head or ghost) belongs to the game too: it drops nothing either and goes when the game ends. Power-ups still drop.
- Players in a game can't open doors and trapdoors, flip levers, press buttons, or use any other block but Hellstorm's. A map can allow blocks with the `hellstorm:usable_in_game` block tag.
- Map validation follows the ways a player or zombie can really walk between rooms: steps, one-block jumps, and drops. Rooms drawn taller than a wall two blocks high, or a fence, between them are no longer joined over its top, so a door in that wall isn't a loophole. A hole one block high no longer joins rooms either.
- A zombie left behind by a game that has ended no longer crashes the server when it moves.
- Every setting that names an item takes NBT the way `/give` does, such as `lrtactical:melee{MeleeWeaponId:"lrtactical:dagger"}`: `players.starting_items`, `players.starting_guns` (merged onto the gun), and in datapacks, a power-up's `item` and any item spec (wall-buys, crate pools, upgrades), where the string works in place of the object. A `tacz_gun` spec takes `nbt` too, so a gun upgrade can carry its own `punch_id`.
- A loophole (a door whose rooms are joined anyway) is a warning again, so it no longer stops a game from starting.
- **Upgrade files have a new shape** (the old one no longer loads). One file in `hellstorm/punch/` holds any number of upgrades keyed by pap id (`"sword_mk2"` is in the file's namespace, `"mymap:sword_mk2"` as written), each with `item-id` and `nbt` (JSON or an SNBT string), plus optional `name`, `cost` and `carry-over`. A TacZ gun id still builds a loaded, punchable gun. A `defaults` object changes every gun of a TacZ class (`pistol`, `smg`, `rifle`, `shotgun`, `sniper`, `mg`, `rpg`) after the default upgrade, by `merge`, `modify` (the default) or `remove`. A bad entry is skipped alone, and the upgrade check reports it, a result that isn't installed, and an unknown gun class.
- Crawlers move at 0.8 of a zombie's speed, up from half.
- In multiplayer, the Mystery Crate's guns flick by smoothly on every player's screen. They used to stutter, jumping back a few guns about once a second, on a server running behind or over an uneven connection.
- With `dismemberment` off, Hellstorm no longer registers a zombie renderer, so another mod's zombie renderer is kept instead of being replaced by Minecraft's.
- A game's ambient tracks play on the Music volume slider instead of Ambient/Environment, and Minecraft's own music stops and holds off while one plays.
- The game start sound is about half as loud.
- A zombie tearing a board off a window now also plays the armor stand break, from the middle of the window.
- Vending machines no longer rattle when they vend, and their hum no longer clicks every 12 seconds.

## v0.0.1-prerelease

The first prerelease, for playtesting. Everything below is built and covered by automated tests, but parts are still being checked in game (`TO_TEST.md`), and game balance, art, and sound are not final. Worlds and map packs made with it may need rework before v0.1.0.

Requires Minecraft 1.20.1, Forge 47 or newer (NeoForge for 1.20.1 also works), and TacZ 1.1.8 or newer on both client and server. GeckoLib and LuaJ are bundled.

**Map making**
- Rooms drawn with the Zone Wand or detected by flood fill, with undo and redo, outlines while a tool is held, and edit and play modes.
- Doors and debris with costs and power requirements; windows of any size up to 5 by 4 with 0 to 6 boards; zombie spawn markers linked to windows.
- `/hs map validate`, which reports every problem with its position and refuses to start a game with errors (including loopholes: openings that make a door pointless).
- Map packs: export a map with its datapack to a file and import it into another world.

**The game**
- Rounds with CoD-style zombie counts, health, and speed, a 5 second break between rounds, and points for hits, kills, headshots, and repairs.
- Wall-buys for TacZ guns or any item, the Mystery Crate, six perks from vending machines (as potions or perk cans), the upgrader, traps, and a power switch.
- Downs and revives (including through grass), bleeding out, spectating, late joining, and co-op on dedicated servers.
- Six power-ups and special hound rounds.
- Zombies lose the arm or leg a gunshot lands on, and a zombie missing a leg crawls. Dismemberment can be switched off.
- Stand-in sounds and ambient music.

**For datapack and map authors**
- Mob profiles (any mob, including modded ones, can be a zombie), power-ups, wave events, crate pools, upgrade definitions, and gun balance as datapack JSON.
- Sandboxed Lua scripts bound to game events.

Documentation is in the project wiki.
