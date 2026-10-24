# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Maps

- [ ] **Walls between rooms.** Draw two rooms that touch and reach well above a wall two blocks high between them, with a Hellstorm door in the wall. `/hs map validate` reports no loophole, and `/hs map paths` lists no connection between them. Lower one stretch of the wall to one block: now it reports a loophole there. A fence instead of the low stretch keeps them apart. Try your real walls too (slabs, stairs, glass panes, leaves) and tell me any that come out wrong.

## Items with NBT

- [ ] **Starting kit with NBT.** With `examples/datapacks/zombies_more_test` and its README's `[players]` settings, start a game: you get the Glock and the LesRaisins dagger, the dagger works as a melee weapon, and after bleeding out and respawning you get both back.
- [ ] **Zombies More zombies.** With that pack, `/hs mobtest zombies_more_test:<name>` for `tank_zombie`, `explosive_zombie`, `crawler`, `cursed_zombie` and `boomer_zombie`: each walks to you and attacks. In a game, about one spawn in 21 is one of them. Note any that stand still, climb out of the map (the crawler is a spider underneath), or break blocks.

## Zombies

- [ ] **No block use in a game.** In a game, right-click a wooden door, a trapdoor, a lever and a chest: none opens or flips, and none flickers. Wall-buys, machines, the power switch, and buying a door still work, and a gun still aims.
- [ ] **Hellhounds and sizes.** In a hound round (round 5, or `/hs mobtest hellstorm:hellhound`), hounds are half again a wolf's size, standing on the ground, with their hitbox (F3+B) around the body. Shoot one in the head and body to check hits land where it's drawn. Its shadow stays wolf-sized.
- [ ] **Runners and crawlers.** About one zombie in ten moves noticeably faster than the rest, and the last zombie of each round speeds up. Crawlers now crawl at about 0.8 of a walker's speed: check the arm animation keeps up.
- [ ] **Dismemberment.** Shoot zombies' arms and legs in a game. An arm comes off (about three pistol shots early on, with a spray of red) and the zombie keeps coming; a leg comes off and it drops to a crawl: lying face down, pulling itself along with its arms, at about half speed, and small enough to shoot low. Check both sides come off the side you shot, the crawl looks right from the front, side, and behind, and the head looks forward. To see one up close, `/summon minecraft:zombie ~ ~ ~ {ForgeData:{hellstorm_crawler:{speed_multiplier:0.5f}}}` makes a crawler (and `{ForgeData:{hellstorm_limbs:5b}}` one missing its left arm and left leg).

## Downs

- [ ] **Hellhounds.** In round 5 a hound has 2 HP and dies to about one pistol shot.
- [ ] **Revive through grass.** Go down in tall grass or flowers. A teammate looking at you through them sees "Revive", and holding the revive key or right click revives you. A solid block in the way (glass, a wall) still blocks it.

## Sounds

- [ ] **Ambient music.** A minute or two into a game a track plays quietly, then another a while after it ends. It should sit under the game, not over it; tell me if any one is too loud or soft against the others. Leaving the game or its end stops it.
- [ ] **Round break.** The wait between rounds is 5 seconds (your Test world's config was updated from 3000 to 5000).
- [ ] **Stand-in sounds.** Play a game and listen to each (`SOUNDS_NEEDED.md` lists them): game start, round start (round 2 on) and end, power on (from the switch: walk away and it fades), a power-up appearing and being picked up, a refused purchase, a door and debris bought (the blocks should burst right on the impact), the upgrader (the slowed anvil, and the louder whoosh lasting as long as the flames), the crate moving, and going down, being revived, and bleeding out. Check volumes against guns, and that the heartbeat stops as soon as you're up.
