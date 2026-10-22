# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Map tools

- [ ] **Selections clear once used.** After `/hs zones add`, `/hs zones cut`, or `/hs zones merge` with a selection, the selection outline is gone. `/hs map export` leaves it.
- [ ] **Windows with 0 to 6 boards.** `/hs zones add window <name> 0` makes an open hole: zombies climb straight in and there's nothing to repair. Other counts start that many boards.

## Machines

- [ ] **Vending machine remap.** A machine selling a colored potion is repainted all over around the potion's color (the purple trim, base, and screen glow turn too; white and greys stay neutral), lit and unlit and through the fade. The keypad's red and green lights keep their colors. Preview of the unlit texture: the scratchpad's `vending_machine_colors_v2.png` from 2026-10-22.

## Guns

- [ ] **Gun balance feel.** Play several rounds each with a pistol, a shotgun, a rifle, and a sniper. None should run dry far sooner than the others, and the buffed weak guns (Glock 17, M9A4, double-barrels) shouldn't feel useless. Numbers in `features/gun-balance-table.md`.
- [ ] **Launchers.** The RPG-7 (9 rockets) and M320 (13 grenades) were balanced by hand. Check they're worth buying and not overpowered.
- [ ] **Minigun.** Fed from the inventory, so its ammo isn't balanced. Check whether it's usable in a game at all.
