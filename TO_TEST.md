# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Map tools

- [ ] **Selections clear once used.** After `/hs zones add`, `/hs zones cut`, or `/hs zones merge` with a selection, the selection outline is gone. `/hs map export` leaves it.
- [ ] **Windows with 0 to 6 boards.** `/hs zones add window <name> 0` makes an open hole: zombies climb straight in and there's nothing to repair. Other counts start that many boards.

## Power-ups and players

- [ ] **Power-ups at eye level.** A dropped power-up floats in the second block above the floor, about eye height, and walking under it picks it up. One from a zombie killed mid-jump floats at the same height.
- [ ] **Pickup cooldown.** After picking up a power-up, it doesn't drop again for the next 25 zombie deaths (others still can).
- [ ] **Knockback.** In a game, a zombie's hit barely pushes you (15% of normal). Outside a game, knockback is normal.

## Machines

- [ ] **Load-Up and Vigoraid cans.** Open the Hellstorm creative tab in a new world (it crashed before the fix) and play with a can in the hotbar (the game slowed to a crawl before the fix). `/give @s hellstorm:load_up` and `/give @s hellstorm:vigoraid`, each in turn. Each view should now match the Blockbench display preview (the first build drew them half a block low). Check in hand (both views), in the inventory, on the ground, and on a vending machine's shelves (it uses the `fixed` transform there; the `on_shelf` one is for 1.21.9's shelf block and isn't read in 1.20.1). Drinking it plays the drinking pose and sound and gives its perk with no time limit and no swirling particles around the player, while the effect icon still shows. A machine set to sell one (hold the can, `/hs wallbuy set 2500 held`) takes the perk's color (Load-Up green, Vigoraid red) and shows cans on its shelves: two per shelf, at the front, 25% smaller than before, standing on the shelf (not sunk into it or floating).
- [ ] **Vending machine hum.** A lit machine's idle hum is about a quarter as loud as before, and still fades with the light.

## Guns

- [ ] **Gun balance feel.** Play several rounds each with a pistol, a shotgun, a rifle, and a sniper. None should run dry far sooner than the others, and the buffed weak guns (Glock 17, M9A4, double-barrels) shouldn't feel useless. Numbers in `features/gun-balance-table.md`.
- [ ] **Launchers.** The RPG-7 (9 rockets) and M320 (13 grenades) were balanced by hand. Check they're worth buying and not overpowered.
- [ ] **Minigun.** Fed from the inventory, so its ammo isn't balanced. Check whether it's usable in a game at all.
