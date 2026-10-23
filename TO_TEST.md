# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Sounds

- [ ] **Round break.** The wait between rounds is 5 seconds (your Test world's config was updated from 3000 to 5000).
- [ ] **Stand-in sounds.** Play a game and listen to each (`SOUNDS_NEEDED.md` lists them): game start, round start (round 2 on) and end, power on (from the switch: walk away and it fades), a power-up appearing and being picked up, a refused purchase, a door and debris bought (the blocks should burst right on the impact), the upgrader (the slowed anvil, and the louder whoosh lasting as long as the flames), the crate moving, and going down, being revived, and bleeding out. Check volumes against guns, and that the heartbeat stops as soon as you're up.
