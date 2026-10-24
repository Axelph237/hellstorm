# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Maps

- [ ] **Walls between rooms.** Draw two rooms that touch and reach well above a wall two blocks high between them, with a Hellstorm door in the wall. `/hs map validate` reports no loophole, and `/hs map paths` lists no connection between them. Lower one stretch of the wall to one block: now it reports a loophole there. A fence instead of the low stretch keeps them apart. Try your real walls too (slabs, stairs, glass panes, leaves) and tell me any that come out wrong.

## Spawning

- [ ] **Zombies spawn only in rooms you've been in.** On a map with a door out of the start room, play a few rounds without buying it: zombies come only from the start room's markers and windows. Buy the door but stay out of the room behind it: still none from that room. Walk in, then back to the start room: that room's markers and windows start spawning, and keep spawning for the rest of the game.

## Mystery Crate

- [ ] **Smooth spin in multiplayer.** On a dedicated server (or LAN, hosted from a second machine), with two clients A and B in a game standing at the crate. Make the server run behind: summon a few hundred mobs away from the arena until `/forge tps` shows well under 20. A rolls the crate several times: on both screens the guns rise and flick forward only, one gun after the next, slowing to a stop, and the dings keep pace with them, never repeating a gun or stuttering back. On a busy server the last gun may hang at the top for a moment before the result swaps in. Then B walks away out of render distance, A rolls, and B comes back mid-roll: B's guns join partway up and run on smoothly. B rolls while A watches. Repeat once with the server not busy.

## Zombies

- [ ] **A crawler tag without a speed.** In a test datapack, add a mob profile `{"entity": "minecraft:zombie", "nbt": "{ForgeData:{hellstorm_crawler:{}}}"}`, `/reload`, and `/hs mobtest <it>`: the zombie crawls toward you, drawn face down, not standing still and drawn upright.
- [ ] **Crawlers crawl at about 0.8 of a walker.** Shoot a zombie's leg off next to a walking zombie and walk away from both: the crawler falls only a little behind, noticeably faster than crawlers were before.
- [ ] **Hounds feel as fast as before.** Play to a hound round (or `/hs mobtest hellstorm:hellhound`): hounds close in as fast as they used to.
