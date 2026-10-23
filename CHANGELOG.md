# Changelog

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
