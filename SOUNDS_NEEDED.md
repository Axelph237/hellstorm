# Sounds needed

Atmospheric sounds and music for a game, listed for you to supply. This mod doesn't make sound files: once you supply one, it's copied in unchanged and wired to its trigger. Until then the trigger plays its stand-in or nothing.

Files go in `assets/hellstorm/sounds/` as `.ogg`, at the path below plus `.ogg`. Sounds heard from a place in the world (a machine, a zombie, a power-up) must be **mono**, or Minecraft won't play them positionally. Music and announcer lines heard by everyone the same can be stereo. A row can have several files (`_1`, `_2`, …) to pick from at random.

The trigger is the game event that plays it (`game/GameEvent`), or the place in the code.

## Music

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `music/game_start` | 5 to 15 s | `GameStart`: the game begins | Everyone in the game | Funeral chant, pitch 0.9 |
| `music/round_start` | 4 to 8 s | `RoundStart`: each round begins (the classic round-change sting) | Everyone in the game | "No Evidence of Disease", faded out from 3 s to 4 s; not on round 1, which starts with the game |
| `music/round_end` | 6 to 10 s | `RoundEnd`: the last zombie of a round dies | Everyone in the game | Fire breath, its silent intro clipped |
| `music/special_round_start` | 5 to 10 s | `WaveEventStarted`: a special round (hounds) begins | Everyone in the game | None |
| `music/game_over` | 10 to 20 s | `GameEnd`: everyone is down or out | Everyone in the game | None |
| `music/ambient` | 1 to 3 min, loops | Played quietly under the whole game, between the stings | Everyone in the game | None |
| `music/fire_sale` | 30 s, loops | While Fire Sale lasts, from every Mystery Box location | Nearby, from the boxes | None |

## Machines

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `perk/<perk_id>_jingle` | 15 to 30 s each | A lit vending machine now and then plays its perk's jingle (vigorade, reload, hellfire, saints_brew, jockey_energy, off_hand) | Nearby, from the machine | None |
| `mystery_box/spin` | 4 to 6 s | `MysteryBoxOpened`: while the weapons cycle | Nearby, from the box | Chest open and close |
| `mystery_box/move` | 3 to 5 s | `MysteryBoxMoved`: the box leaves (CoD's teddy-bear laugh) | Nearby, from the old place | Wither death, the scary laugh, and soul speed cries rising and falling in pitch, at the old place |
| `pap/upgrade` | 3 to 5 s | `PapUsed`: the upgrader takes and returns a weapon | Nearby, from the machine | Fire whoosh, stretched to the 3 s the item burns, pitch 0.9 (`upgrader/fire`) |
| `power/on` | 2 to 4 s | `PowerOn`: the power switch is thrown, a heavy clunk and rising hum | Everyone in the game | Large metal door close at 3/4 volume, with the generator fading out from 10 s to silence at 15 s, to everyone in the game |
| `trap/activate` | 1 to 2 s | `TrapActivated` | Nearby, from the controller | None |
| `purchase/denied` | under 1 s | Any purchase refused for too few points | The buyer | The guardian's second hit, volume 0.5, pitch 0.85, for any refusal |
| `door/debris_clear` | 1 to 2 s | `DoorOpened`, for debris | Nearby, from the debris | Cha-ching and dark magic impact; the blocks burst on its peak |

## Power-ups

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `powerup/spawn` | under 1 s | `PowerUpSpawned`: a power-up appears | Nearby, from the drop | The elder guardian's curse at pitch 1.2 (bad omen is 1.20.5's; 1.20.1 has none) |
| `powerup/loop` | 2 to 4 s, loops | While a power-up floats on the ground | Nearby, from the drop | None |
| `powerup/grab` | under 1 s | `PowerUpCollected`: the pickup sting | Everyone in the game | The raid horn at pitch 0.9, volume 0.6 (raid omen is 1.20.5's), with the ravager's first celebrate at pitch 0.9, to everyone in the game |
| `announcer/<powerup_id>` | 1 to 2 s each | `PowerUpCollected`: the announcer names it (max_ammo, insta_kill, double_points, nuke, carpenter, fire_sale) | Everyone in the game | None |
| `powerup/timer_end` | under 1 s | Insta-Kill, Double Points, or Fire Sale running out | Everyone in the game | None |

## Players

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `player/downed` | 1 to 2 s | `PlayerDowned` | Everyone in the game | None |
| `player/bleeding` | 1 to 2 s, loops | While a player is down: heartbeat and ringing | The downed player | Heartbeat, looping from just before one beat to just before the matching one, to the downed player alone |
| `player/revived` | 1 to 2 s | `PlayerRevived` | Nearby, from the player | The loud heartbeat's first two beats, to the revived player |
| `player/bled_out` | 1 to 3 s | `PlayerBledOut` | Everyone in the game | Dark bell, pitch 0.85, volume 0.6, to everyone in the game |

## Zombies

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `zombie/spawn` | 1 to 2 s | `ZombieSpawned`: a zombie climbs out at its spawn | Nearby, from the spawn | None |
| `zombie/ambient`, `zombie/sprint`, `zombie/attack`, `zombie/death` | 1 to 2 s each, several of each | Game zombies' voices in place of vanilla's, walking and sprinting apart | Nearby, from the zombie | Vanilla zombie sounds |
| `hound/spawn` | 1 to 2 s | A hellhound appears (CoD's lightning strike) | Nearby, from the spawn | None |
| `window/rebuild` | under 1 s | A board goes back on a window | Nearby, from the window | Armor stand break |

## Stand-ins in place (2026-10-23)

The rows above whose stand-in names a recording play it now. `game/GameSounds` holds each as a cue: its sounds with their delays, volumes, and pitches. It plays a cue for a game event at a place (`at`, heard by those nearby), to one player (`to`, as is, wherever they turn), or to every player in the game. A few play where they happen instead: the upgrader's fire in `PapMachineBlock`, the debris in `Doors` (with `DoorFlights.burst`), the denied sound in `Shop.refuse`, and the bleeding heartbeat on the downed player's own client (`client/BleedingSound`, which stops itself once they're up or out).

- **Files.** Your recordings are in `sounds/mystery_box/`, `power/`, `debris/`, `music/`, `upgrader/` and `player/` as Ogg Vorbis, cut by a script from the MP3s you supplied: mono where they're placed in the world (the laugh, the impact, the upgrader's fire), stereo where they're played to players. Pitch and volume are set in the cues, not baked in; cuts, fades, and the upgrader's stretch (a phase vocoder, so its pitch holds) are baked in.
- **Minecraft's own sounds** are named in `sounds.json` (soul speed, the curse, the raid horn, the ravager's `celebrate1`, the guardian's `guardian_hit2`) or used as they are (the wither's death), never copied.
- **Debris burst.** Debris now rises together 1 block, shrinking to 0.85, swells to 1.25 over 2 ticks at the impact's peak (0.95 s in, `GameSounds.DEBRIS_PEAK_TICKS`, 19 ticks), and shrinks to nothing over 3. Bought doors keep their wave.
- **Replaced.** The power-up pickup's beacon, the upgrader's fire charge, and debris's gravel break.
- **Tests.** GameTest `GameSoundsGameTests`: each game event plays its cue to the players in the game, round 1 plays none, and a player outside hears nothing. `DoorGameTests.debrisRisesThenBurstsOnTheImpactsPeak`: the copies are at 0.85 before the peak, 1.25 on it, 0 after, and gone in time. `PurchaseGameTests` checks a refused purchase plays the denied sound once.
- **Not yet played:** the ambient music, Fire Sale's loop, the perk jingles, the box spin, the trap, the power-up's floating loop and timer end, the downed sting, zombie and hound voices, and the window rebuild.
