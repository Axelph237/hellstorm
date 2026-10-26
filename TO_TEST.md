# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Screens (2026-10-25)

- [ ] **Starter UI on the machines' settings screens.** In creative, use each of: a wall-buy (holding a gun), a vending machine, the Mystery Crate, the upgrader, a trap controller and the power switch. Each: the world behind is darkened to 90%, with a clear margin between the frame and the rows, the frame surrounds the rows with the title sitting in the gap of its top edge, and the flames in its bottom right corner don't touch any button. Buttons, text fields and the wall-buy's item slot are the input box, its edges unbroken at every size (wide buttons, the 30-tall number fields, the small item slot), and hovering a button or dropdown lifts it faintly. Number fields have the + and − halves beside them: each click steps costs by 50 (500 with Shift), the trap's ticks by 20 and its signal by 1, and never below 0. "Needs power" is a toggle that saves. Open "Buying again" (wall-buy) or "Pool" (crate): a list of boxes stacks under it, the chosen one green, the mouse wheel scrolls a long list, a click picks, Escape or a click outside closes it. Done saves and closes; a refused value shows in red above the buttons. Try GUI Scale 2 to 4: at 4 on a 1080p screen the wall-buy with ammo rows is the tallest and should still fit.

## Machines and perks (2026-10-24)

- [ ] **Saint's Brew on the shelves.** Sell a Saint's Brew from a vending machine: the machine is amber, the bottle's own color, not blue, and the six bottles stand on the shelves the same size as Vigoraid's and Load-Up's cans, bottoms on the shelf, labels facing out. The Saint's Brew effect icon's stand-in square is amber too.
- [ ] **Drink lore.** Hover each can: Vigoraid "Doubles HP" (HP red), Load-Up "Doubles reload speed" (reload green), Saint's Brew "Doubles revive speed" (revive gold), the rest white, not italic. In a game, look at a vending machine selling a can: the prompt hangs in front of the machine's top, not over it, with the lore on a smaller line under the name and price, in both prompt styles (in the world, and on the HUD with `prompt_style = HUD`). A drink with a `Lore` tag (`/give @s hellstorm:load_up{display:{Lore:['"Tastes like iron"']}}`) shows that line instead.

## Zone Wand (2026-10-24)

- [ ] **Corners in front of the face, sneak for the block.** With the Zone Wand, look at the floor: the preview box starts in the air above it. Look at a wall: it's in the air in front of the wall, and under a ceiling it's the air below. Sneak: it moves onto the block itself. Click a door, a lever and a chest: none opens or flips, standing or sneaking, and the corner is set. `/hsz desel` clears the selection, and `/hsz add room <name>` works like `/hs zones add room`.

## Fixes from the known-issues pass (2026-10-24)

- [ ] **Script sandbox.** The pack is in your Test world (`run/saves/Test/datapacks/hellstorm_sandbox_check`). `/reload` and join a game: the log's sandbox lines all say "blocked", and TacZ's default guns still fire, animate and reload normally. The pack also has a runaway `round_start` listener: each round logs an instruction budget error, and the server keeps ticking. Remove the pack afterwards.
- [ ] **Scripting off.** Set `scripting.enabled = false`, join a game: `/hs scripts reload` says scripting is off; `/hs scripts check` warns.
- [ ] **Import replace.** Import a pack with `replace` over a map: the old wall-buys, crates and barricades are gone, the terrain stays.
- [ ] **Validation.** Set every mob profile's weight to 0 in a datapack: validation refuses the game. A wall-buy for a gun from a removed gun pack is a warning.

## Mystery Crate

- [ ] **Smooth spin in multiplayer.** On a dedicated server (or LAN, hosted from a second machine), with two clients A and B in a game standing at the crate. Make the server run behind: summon a few hundred mobs away from the arena until `/forge tps` shows well under 20. A rolls the crate several times: on both screens the guns rise and flick forward only, one gun after the next, slowing to a stop, and the dings keep pace with them, never repeating a gun or stuttering back. On a busy server the last gun may hang at the top for a moment before the result swaps in. Then B walks away out of render distance, A rolls, and B comes back mid-roll: B's guns join partway up and run on smoothly. B rolls while A watches. Repeat once with the server not busy.
