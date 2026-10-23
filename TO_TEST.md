# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Map tools

- [ ] **Command errors.** Run a few failing commands: `/hs map validate` on a map with a gap around a door, `/hs zones remove room nosuchroom`, `/hs game start` with no game. Each shows a red underlined title ("Unable to validate map"), then `ERROR:` in red and the message in white with room names, numbers, positions and commands in gold, and nothing else underlined.
- [ ] **Loopholes block games.** Leave a gap above a door (or draw a room taller than its wall). `/hs map validate` reports "Loophole found between <room> and <room> at (x, y, z)" with a red marker at that spot while you hold a map tool, and `/hs game join` refuses to start the game with the same error.
- [ ] **Windows with 0 to 6 boards.** `/hs zones add window <name> 0` makes an open hole: zombies climb straight in and there's nothing to repair. Other counts start that many boards.

## Power-ups and players

- [ ] **Knockback.** In a game, a zombie's hit barely pushes you (15% of normal), and your own rocket or grenade at your feet hurts but doesn't throw you (an M320 or RPG-7 into the floor nearby). Outside a game, both push as usual.
- [ ] **No repeat power-ups.** Over a few rounds, the same power-up never drops twice within 25 zombie deaths, even when the first is left on the ground.

## Guns

- [ ] **Launchers.** The RPG-7 (9 rockets) and M320 (13 grenades) were balanced by hand. Check they're worth buying and not overpowered.
- [ ] **Minigun.** Fed from the inventory, so its ammo isn't balanced. Check whether it's usable in a game at all.
