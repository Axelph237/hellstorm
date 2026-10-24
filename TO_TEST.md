# To test in game

What's built and covered by automated tests but not yet checked by playing. Tick an item off (delete it) once you've seen it work; note anything wrong under it.

## Maps

- [ ] **Walls between rooms.** Draw two rooms that touch and reach well above a wall two blocks high between them, with a Hellstorm door in the wall. `/hs map validate` reports no loophole, and `/hs map paths` lists no connection between them. Lower one stretch of the wall to one block: now it reports a loophole there. A fence instead of the low stretch keeps them apart. Try your real walls too (slabs, stairs, glass panes, leaves) and tell me any that come out wrong.

## Spawning

- [ ] **Zombies spawn only in rooms you've been in.** On a map with a door out of the start room, play a few rounds without buying it: zombies come only from the start room's markers and windows. Buy the door but stay out of the room behind it: still none from that room. Walk in, then back to the start room: that room's markers and windows start spawning, and keep spawning for the rest of the game.

## Items with NBT

- [ ] **Starting kit with NBT.** With `examples/datapacks/zombies_more_test` and its README's `[players]` settings, start a game: you get the Glock and the LesRaisins dagger, the dagger works as a melee weapon, and after bleeding out and respawning you get both back.
- [ ] **Zombies More zombies.** With that pack, `/hs mobtest zombies_more_test:<name>` for `tank_zombie`, `explosive_zombie`, `crawler`, `cursed_zombie` and `boomer_zombie`: each walks to you and attacks. In a game, about one spawn in 21 is one of them. Note any that stand still, climb out of the map (the crawler is a spider underneath), or break blocks.

## Mystery Crate

- [ ] **Smooth spin in multiplayer.** On a dedicated server (or LAN, hosted from a second machine), with two clients A and B in a game standing at the crate. Make the server run behind: summon a few hundred mobs away from the arena until `/forge tps` shows well under 20. A rolls the crate several times: on both screens the guns rise and flick forward only, one gun after the next, slowing to a stop, and the dings keep pace with them, never repeating a gun or stuttering back. On a busy server the last gun may hang at the top for a moment before the result swaps in. Then B walks away out of render distance, A rolls, and B comes back mid-roll: B's guns join partway up and run on smoothly. B rolls while A watches. Repeat once with the server not busy.

## Upgrade files

- [ ] **Upgrades and gun class defaults.** Put `examples/datapacks/hellstorm_example` in the world and `/reload`; the log has no `Upgrader:` errors. In a game with power on: the shipped iron sword (`/give @s iron_sword{pap:{punchable:1b,punch_id:"hellstorm:iron_sword"}}`) still becomes the Upgraded Sword for 2500; `/give @s stick{pap:{punchable:1b,punch_id:"hellstorm_example:stick"}}` becomes a Fire Aspect II blaze rod with "Still warm" lore for 500. Upgrade a pistol from a wall-buy: it's "Upgraded ..." with "Hellfire-forged" lore. Upgrade a rifle: it fires about 1.5 times as fast as stock. Upgrade an SMG: it keeps its plain name. Then change a `method` to `"replace"` in the pack, `/reload`, and rejoin as op: the chat notice and `/hs map validate` name the bad entry.

## Zombies

- [ ] **Zombies crossing rooms.** On the Test map, stand in the start room and let zombies come in at the hall window: they come through the doorway beside the trapdoor wall without bunching up or stopping, and don't walk into the trapdoors. Then buy the door and stand at the far end of the room behind it: zombies come the long way round without standing still. With `/hs debug show_paths true` and the Zone Wand in hand, each zombie's red line leads toward you or to a point on the way. With 24 zombies alive, check `/forge tps` stays near 20.
- [ ] **No block use in a game.** In a game, right-click a wooden door, a trapdoor, a lever and a chest: none opens or flips, and none flickers. Wall-buys, machines, the power switch, and buying a door still work, and a gun still aims.
- [ ] **Hellhounds and sizes.** In a hound round (round 5, or `/hs mobtest hellstorm:hellhound`), hounds are half again a wolf's size, standing on the ground, with their hitbox (F3+B) around the body. Shoot one in the head and body to check hits land where it's drawn. Its shadow stays wolf-sized.
- [ ] **Runners and crawlers.** About one zombie in ten moves noticeably faster than the rest, and the last zombie of each round speeds up: walk (don't sprint) away from one and it keeps pace with you, no faster. From round 9 every zombie is about that fast already, so the last one doesn't speed up. Crawlers now crawl at about 0.8 of a walker's speed: check the arm animation keeps up, and that a running zombie's walk animation doesn't look like it's sliding.
- [ ] **No drops.** With the Zombies More pack, play until a few of its zombies die, the boomer and the cursed zombie among them: nothing drops, no XP, and whatever they leave behind drops nothing when killed and is gone once the game ends. Power-ups still drop as before.
- [ ] **Dismemberment.** Shoot zombies' arms and legs in a game. An arm comes off (about three pistol shots early on, with a spray of red) and the zombie keeps coming; a leg comes off and it drops to a crawl: lying face down, pulling itself along with its arms, at about half speed, and small enough to shoot low. Check both sides come off the side you shot, the crawl looks right from the front, side, and behind, and the head looks forward. To see one up close, `/summon minecraft:zombie ~ ~ ~ {ForgeData:{hellstorm_crawler:{speed_multiplier:0.5f}}}` makes a crawler (and `{ForgeData:{hellstorm_limbs:5b}}` one missing its left arm and left leg).

## Windows

- [ ] **Repair cap per window.** In a game, let a zombie tear a window down and rebuild it, again and again, in one round. After ten paid boards (100 points) the boards still go on but the points and the cha-ching stop. Rebuild a different window: it pays again. In the next round the first window pays again. Check `repair_cap_per_window = 100` appears under `[economy]` in the world's `serverconfig/hellstorm-server.toml` with its comment.

## Friendly fire

- [ ] **Off by default.** In a two-player game, shoot your teammate, hit them with the knife, and blow up a grenade or rocket by them: they take no damage. Your own blast still hurts you. Set `friendly_fire = true` under `[players]` in `serverconfig/hellstorm-server.toml` and start a new game: all three hurt them now.

## Downs

- [ ] **Hellhounds.** In round 5 a hound has 2 HP and dies to about one pistol shot.
- [ ] **Revive through grass.** Go down in tall grass or flowers. A teammate looking at you through them sees "Revive", and holding the revive key or right click revives you. A solid block in the way (glass, a wall) still blocks it.

## Sounds

- [ ] **Ambient music.** A minute or two into a game a track plays quietly, then another a while after it ends. It should sit under the game, not over it; tell me if any one is too loud or soft against the others. Leaving the game or its end stops it.
- [ ] **Ambient on the music slider.** In a game, wait for an ambient track (a minute or two in). Turning Music down in Options, Music & Sounds turns it down; Ambient/Environment no longer does. If a Minecraft song was playing, it stops when the track starts, and no Minecraft song starts over the track.
- [ ] **Quieter game start.** Start a game: its chant is about half as loud as before. Say if it's now too quiet against the round start or guns.
- [ ] **Board torn off.** Let a zombie tear boards off a window: each board makes the armor stand's wooden clatter, from the window, with the old bang. Walk 16 or more blocks away and it fades out.
- [ ] **Vending machine.** Buy from a vending machine: the keys click and the machine shakes with no rattle. Stand by a lit machine for half a minute: its hum loops with no click and no seam.
- [ ] **Round break.** The wait between rounds is 5 seconds (your Test world's config was updated from 3000 to 5000).
- [ ] **Stand-in sounds.** Play a game and listen to each (`SOUNDS_NEEDED.md` lists them): game start, round start (round 2 on) and end, power on (from the switch: walk away and it fades), a power-up appearing and being picked up, a refused purchase, a door and debris bought (the blocks should burst right on the impact), the upgrader (the slowed anvil, and the louder whoosh lasting as long as the flames), the crate moving, and going down, being revived, and bleeding out. Check volumes against guns, and that the heartbeat stops as soon as you're up.
