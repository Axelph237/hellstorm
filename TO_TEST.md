# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Machines and perks (2026-10-24)

- [ ] **Drink lore.** Hover each can in the inventory: Vigoraid says "Doubles HP", Load-Up "Increases reload speed by 100%", Saint's Brew "Increases revive speed by 100%", in purple italics. In a game, look at a vending machine selling a can: its lore shows on a line under the name and price, in both prompt styles (in the world, and on the HUD with `prompt_style = HUD`). Sell a drink with a `Lore` tag (`/give @s hellstorm:load_up{display:{Lore:['"Tastes like iron"']}}`): the prompt shows that line instead.

- [ ] **Saint's Brew can.** `/give @s hellstorm:saints_brew`: it shows its model in hand (first and third person), on the ground, in a frame and in the inventory, and drinking it gives Saint's Brew. Sell it from a vending machine: the machine takes its color.
- [ ] **Five perks.** In a game, a sixth perk is refused with "You can only hold 5 perks". Check `perk_limit = 5` in a fresh world's `serverconfig/hellstorm-server.toml`.
- [ ] **Machines without commands.** `/hs wallbuy`, `/hs box`, `/hs upgrader`, `/hs power` and `/hs trap` are unknown commands; each machine's settings screen still sets everything. A non-op using a machine outside a game sees nothing.

## Zone Wand (2026-10-24)

- [ ] **Corners above, sneak for the block.** With the Zone Wand, look at the floor: the preview box starts in the air above it. Sneak: it moves onto the floor block. Click a door, a lever and a chest: none opens or flips, standing or sneaking, and the corner is set. `/hsz desel` clears the selection, and `/hsz add room <name>` works like `/hs zones add room`.

## Fixes from the known-issues pass (2026-10-24)

- [ ] **Script sandbox.** With `examples/datapacks/hellstorm_sandbox_check` in a singleplayer world, `/reload` and join a game: the log's sandbox lines all say "blocked", and TacZ's default guns still fire, animate and reload normally.
- [ ] **Scripting off.** Set `scripting.enabled = false`, join a game: `/hs scripts reload` says scripting is off; `/hs scripts check` warns.
- [ ] **Carpeted rooms.** Detect a room floored with carpet or slabs: the outline covers the floor layer, and standing on the carpet works. A cobweb door stops detection. Walking onto a carpeted room in a game wakes its spawns.
- [ ] **Window tools.** `/hs zones set window <w> boards 2`, then `/hs undo`. Redraw the room behind a window: its outline colour follows.
- [ ] **Import replace.** Import a pack with `replace` over a map: the old wall-buys, crates and barricades are gone, the terrain stays.
- [ ] **Lobby edits.** Open a lobby, add a room, `/hs game start`: the room is in the game. Open a lobby, unflag the start room, start: it's refused and the lobby stays.
- [ ] **Validation.** Put the only power switch behind a door that needs power: `/hs map validate` reports it as an error. Set every mob profile's weight to 0 in a datapack: validation refuses the game. A wall-buy for a gun from a removed gun pack is a warning.

## Maps

- [ ] **Walls between rooms.** Draw two rooms that touch and reach well above a wall two blocks high between them, with a Hellstorm door in the wall. `/hs map validate` reports no loophole, and `/hs map paths` lists no connection between them. Lower one stretch of the wall to one block: now it reports a loophole there. A fence instead of the low stretch keeps them apart. Try your real walls too (slabs, stairs, glass panes, leaves) and tell me any that come out wrong.

## Spawning

- [ ] **Zombies spawn only in rooms you've been in.** On a map with a door out of the start room, play a few rounds without buying it: zombies come only from the start room's markers and windows. Buy the door but stay out of the room behind it: still none from that room. Walk in, then back to the start room: that room's markers and windows start spawning, and keep spawning for the rest of the game.

## Wall-buys, vending machines and commands

- [ ] **A full gun isn't refilled.** In a game, buy an AK-47 from a wall-buy and look at the wall-buy again: no prompt shows, and using it says "That gun's ammo is full" and costs nothing. Fire a few rounds: the prompt comes back with the ammo price, and buying fills the gun. Look at the wall-buy with the starting pistol (unlimited ammo) in hand, if a wall-buy sells it: no prompt, and using it says "That gun has unlimited ammo".
- [ ] **The minigun's reserve.** Buy a `tacz:minigun` from a wall-buy in a game: the ammo HUD shows 150 in reserve at the default `players.reserve_magazines` of 4, and it fires all 150 before it runs dry. (`/hs give tacz:minigun` gives 9 magazines, 270.)
- [ ] **`/hs game status`.** During a round it reads like "Round 3: 12 zombies still to spawn, 4 alive", between rounds "Round 4 starts in 10 s", and each player's line says alive, down (with the bleed-out time, or being revived), spectating until the next round, or waiting for the start. Go down and check it says down, not spectating.
- [ ] **Vending machine behind a lagging server.** On a dedicated server (or LAN from a second machine), make the server run behind: summon a few hundred mobs away from the arena until `/forge tps` shows well under 20. Buy from a vending machine a few times: the three key clicks play once each, in time with the keys, and the shake never starts over. Its light fade and hum stay smooth when the power goes on. Put a gun in the upgrader: the flames grow once and flicker smoothly.

## Mystery Crate

- [ ] **Smooth spin in multiplayer.** On a dedicated server (or LAN, hosted from a second machine), with two clients A and B in a game standing at the crate. Make the server run behind: summon a few hundred mobs away from the arena until `/forge tps` shows well under 20. A rolls the crate several times: on both screens the guns rise and flick forward only, one gun after the next, slowing to a stop, and the dings keep pace with them, never repeating a gun or stuttering back. On a busy server the last gun may hang at the top for a moment before the result swaps in. Then B walks away out of render distance, A rolls, and B comes back mid-roll: B's guns join partway up and run on smoothly. B rolls while A watches. Repeat once with the server not busy.

## Zombies

- [ ] **A crawler tag without a speed.** In a test datapack, add a mob profile `{"entity": "minecraft:zombie", "nbt": "{ForgeData:{hellstorm_crawler:{}}}"}`, `/reload`, and `/hs mobtest <it>`: the zombie crawls toward you, drawn face down, not standing still and drawn upright.
- [ ] **Crawlers crawl at about 0.8 of a walker.** Shoot a zombie's leg off next to a walking zombie and walk away from both: the crawler falls only a little behind, noticeably faster than crawlers were before.
- [ ] **Hounds feel as fast as before.** Play to a hound round (or `/hs mobtest hellstorm:hellhound`): hounds close in as fast as they used to.
