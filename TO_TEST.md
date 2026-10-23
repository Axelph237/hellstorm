# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Downs

- [ ] **Hellhounds.** In round 5 a hound has 2 HP and dies to about one pistol shot.
- [ ] **Revive through grass.** Go down in tall grass or flowers. A teammate looking at you through them sees "Revive", and holding the revive key or right click revives you. A solid block in the way (glass, a wall) still blocks it.

## Sounds

- [ ] **Ambient music.** A minute or two into a game a track plays quietly, then another a while after it ends. It should sit under the game, not over it; tell me if any one is too loud or soft against the others. Leaving the game or its end stops it.
- [ ] **Round break.** The wait between rounds is 5 seconds (your Test world's config was updated from 3000 to 5000).
- [ ] **Stand-in sounds.** Play a game and listen to each (`SOUNDS_NEEDED.md` lists them): game start, round start (round 2 on) and end, power on (from the switch: walk away and it fades), a power-up appearing and being picked up, a refused purchase, a door and debris bought (the blocks should burst right on the impact), the upgrader (the slowed anvil, and the louder whoosh lasting as long as the flames), the crate moving, and going down, being revived, and bleeding out. Check volumes against guns, and that the heartbeat stops as soon as you're up.
