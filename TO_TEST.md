# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Map tools

- [ ] **Room planks.** Holding the Zone Wand, each nearby room's surface shows faint oak planks in the room's color, from inside the room (on its walls and floor) and from outside. (`zone-tools-and-outlines.md`)
- [ ] **Wand corners.** A corner is the block you're looking at, which makes selecting a door's own blocks easy.
- [ ] **Window workflow.** Fill a hole in a room's wall with any blocks, select them, `/hs zones add window <name>`: the blocks become boards.
- [ ] **Wand hint.** After the second click, a selection that touches no room shows `/hs zones add <room|window|door>`; one that touches a room shows nothing. No "First corner" or size text appears.
- [ ] **Undo and redo.** `/hs undo` and `/hs redo` after adding a room, a door, and a window. Undoing the window puts back the blocks you filled the hole with.
- [ ] **Merge and cut.** `/hs zones merge` with a selection across two rooms; `/hs zones cut` across a room and a door.

## Machines

- [ ] **Vending machine color.** A machine selling a perk potion shows the potion's color, unlit and lit, and through the one-second fade when the power changes. One selling a non-colored item stays green. (`perk-machines.md`)

## Guns

- [ ] **Gun balance feel.** Play several rounds each with a pistol, a shotgun, a rifle, and a sniper. None should run dry far sooner than the others, and the buffed weak guns (Glock 17, M9A4, double-barrels) shouldn't feel useless. Numbers in `features/gun-balance-table.md`.
- [ ] **Launchers.** The RPG-7 (9 rockets) and M320 (13 grenades) were balanced by hand. Check they're worth buying and not overpowered.
- [ ] **Minigun.** Fed from the inventory, so its ammo isn't balanced. Check whether it's usable in a game at all.
- [ ] **Starting gun ammo.** In a new world, the starting pistol's reserve never runs out (the magazine still needs reloading). An existing world keeps its saved `players.starting_guns_unlimited_ammo`.

## Zombies

- [ ] **Far aggro.** Zombies and hounds come for a player far away and out of sight, including the long way round. The original report wasn't reproduced exactly: note where it still happens (zombie or hound, map, distance).
- [ ] **Locked rooms.** On a map with a closed door, no zombie spawns behind it until it's bought.

## Release

- [ ] **The built jar.** `build/libs/hellstorm-<version>.jar` loads on a client, alone and alongside Create 6.0.8.

## Open questions

- Should the `door_not_needed` warning (rooms joined through a gap zombies can't climb) block a game from starting?
