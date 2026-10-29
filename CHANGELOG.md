# Changelog

## Unreleased

- Looking at a vending machine shows the drink's lore on a smaller line under its name and price, with the prompt hanging in front of the machine. Each can says what it does, in white with one word in color: Vigoraid "Doubles HP", Load-Up "Doubles reload speed", Saint's Brew "Doubles revive speed". A drink with its own `Lore` tag shows that instead.
- Using a machine outside a game as anyone but an op in creative does nothing; it no longer shows the machine's settings in chat.
- The `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` commands are gone: set every machine on its settings screen (use it as an op in creative, outside a game). To sell a gun, hold it, using `/hs give <gun>` if needed.
- New: a Saint's Brew can, `hellstorm:saints_brew`, with its own model.
- New: an Off-Hand can, `hellstorm:off_hand` ("Carries a third gun", 4000 points by default), and a Wandering Spirit can, `hellstorm:jockey_energy` ("Boosts movement speed", 2000), each with its own model.
- New: a Hellfire can, `hellstorm:hellfire` ("Boosts fire rate", 2000), with its own model. Every perk now comes in a can.
- A new vending machine sells a Vigoraid can instead of a Vigoraid potion. Machines already set keep what they sell. A `perk_bought` event for a can gives the perk's id, such as `hellstorm:vigorade`, as it does for a potion.
- Machine settings screens use the new input art: boxes, buttons and dropdowns light up when hovered, focused or pressed; yes/no settings are the new switch; and a number field's − and + sit in its box's bottom left and top right corners, so number rows are no taller than the rest.
- A wall-buy's plate fits the gun it sells, with a thin border: long for a rifle, and never smaller than three quarters of a block square.
- The HUD is rearranged. The round and the zombies left are at the top left. A row per player sits in the middle of the right side: their points (as $1500), the first 6 letters of their name, and their face (red while they're down, a skeleton's once dead), with the +N rising off yours. Every part of the HUD (the round, the player board, the power-up timers and the progress bar) has its own section in the client config, `[hud.round]` and so on, with an `x` and `y` from 0 to 1 that put it anywhere on screen and `hidden` to turn it off. The player board's `justify` turns its rows around: `LEFT` reads face, name, points.
- The power-up timers across the top of the HUD show each power-up's item beside its name. Max Ammo floats as TacZ's diamond ammo box (was a spectral arrow) and Carpenter as a netherite axe (was oak planks).
- A player can hold 5 perks by default (`players.perk_limit`, was 4).
- The Zone Wand puts a corner in front of the face you click: in the air over a floor, beside a wall, under a ceiling; sneak-click to take the block itself. A wand click never uses the block (no door opens, no lever flips). `/hs zones desel` clears the selection (sneak-click used to), and `/hsz` is short for `/hs zones`.
- **Scripts can no longer escape their sandbox.** String methods such as `("x"):rep(n)` use the script's own capped string table, never the one TacZ's gun scripts share, and `getmetatable("")` gives the script's own. `scripting.max_string_length` now caps `..`, `string.format`, `string.gsub` and `table.concat` too, `..` costs budget for what it builds, and a listener that runs the server out of memory is switched off.
- Script errors name the line that failed, not the function's first line. An event a script's command fires at once no longer refills its instruction budget. `game_start` counts as round 1 for round filters, and its table has `round = 1`.
- `/hs scripts reload` with `scripting.enabled` off says scripting is off (and stops the game's scripts), instead of "Scripts reloaded"; `/hs scripts check` warns about it.
- Room detection takes in carpets, slabs, stairs and paths on the floor, and a door made of cobwebs or vines stops it. A room floored with them is discovered when a player walks onto it.
- `/hs zones add window` no longer fails with "An unexpected error occurred" when the opening's corner has no room behind it, and windows name the room behind them as it is now. New: `/hs zones set window <window> boards <0-6>`.
- `/hs map import <name> replace` takes the old map's machines, markers and barricades out first, and a machine that isn't part of the game's map sells nothing.
- `/hs game start` rebuilds the game from the map as it is then, so rooms and machines changed while the lobby was open are in the game; a map broken in the meantime is refused and the lobby stays open.
- A round no longer stalls forever on a mob profile that's missing or can't make its mob: the game never picks one, and logs a round with nothing to spawn. `/hs map validate` reports a round no profile can spawn in (an error), broken mob profiles, wave overrides of profiles that can't spawn, Mystery Crate pools with nothing to give, machines whose settings are no longer valid (a wall-buy for a gun that's gone), datapack files skipped at the last reload, a power switch only reachable through a door that needs power (an error), and a power switch or upgrader in no room.
- A wall-buy no longer charges for ammo a gun can't take: a full gun, or one with unlimited ammo, is refused for nothing, and a full gun shows no prompt at its wall-buy. A refill chambers a round. A gun that loads from ammo items gets its magazine filled and keeps loading from its items, where a refill used to leave it with an empty dummy reserve.
- The minigun carries one more magazine of reserve (150 rounds at the default 4 magazines, not 120), since it fires from its reserve and never uses its magazine.
- `/hs game status` reads as sentences ("Round 3: 12 zombies still to spawn, 4 alive"), and shows a downed player as down, with their bleed-out time, instead of spectating.
- Text calls the crate the Mystery Crate everywhere, including the `/hs box` command errors, the server config's `[box]` comments and the log. A crate whose pool isn't loaded tells players it is empty and logs the pool for the server owner.
- Behind a lagging server, a vending machine's key clicks play once and its shake doesn't start over, and its light, hum and the upgrader's flames run smoothly: they keep their own time on each client.
- **A mob profile's `speed_multiplier` now means how many times as fast the mob moves**, and so does the crawler tag's. A mob's ground speed goes with the square of its speed attribute, so 1.5 used to move a mob 2.25 times as fast. The hellhound's is now 2.25, so hounds are as fast as they were. Crawlers now move at 0.8 of a walker, up from 0.64, and a running crawler at 0.8 of a runner. A pack's own multipliers above 1 now make its mobs slower than before, and those below 1 faster: 1.5 moves 1.5 times as fast where it used to be 2.25.
- **A datapack file with any error is skipped whole**, with a log line, for mob profiles, power-ups, wave events, crate pools, gun balance and script event defs. Before, an out-of-range value loaded anyway: `"weight": -1`, or `speed_multiplier: 0` for zombies that never move. A mob profile's `nbt` must parse, and a crawler tag's speed must be above 0.
- A crawler tag without a `speed_multiplier` crawls at the usual 0.8 and is drawn crawling. It used to freeze the zombie and draw it standing.
- Trap active times and cooldowns are limited to 72000 ticks (an hour). `/hs trap active` and `/hs trap cooldown` refuse more. Until now they took any number, and a trap with more lost all its settings (name, cost, power, signal) the next time the world saved, without a word. The server config's `traps.default_active_ticks` goes up to 72000 too, from 12000.
- A zombie no longer spawns in a chunk that isn't ticking entities, where it would stand frozen (or stay hidden) and hold the round open; the game tries again on its next spawn.
- A gun with unlimited ammo, such as the starting pistol, shows ∞ for its reserve on the ammo HUD, as in creative, instead of a number.
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
