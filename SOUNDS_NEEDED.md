# Sounds needed

Atmospheric sounds and music for a game, listed for you to supply. This mod doesn't make sound files: once you supply one, it's copied in unchanged and wired to its trigger. Until then the trigger plays its stand-in (a vanilla sound) or nothing.

Files go in `assets/hellstorm/sounds/` as `.ogg`, at the path below plus `.ogg`. Sounds heard from a place in the world (a machine, a zombie, a power-up) must be **mono**, or Minecraft won't play them positionally. Music and announcer lines heard by everyone the same can be stereo. A row can have several files (`_1`, `_2`, …) to pick from at random.

The trigger is the game event that plays it (`game/GameEvent`), or the place in the code.

## Music

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `music/game_start` | 5 to 15 s | `GameStart`: the game begins | Everyone in the game | None |
| `music/round_start` | 4 to 8 s | `RoundStart`: each round begins (the classic round-change sting) | Everyone in the game | None |
| `music/round_end` | 6 to 10 s | `RoundEnd`: the last zombie of a round dies | Everyone in the game | None |
| `music/special_round_start` | 5 to 10 s | `WaveEventStarted`: a special round (hounds) begins | Everyone in the game | None |
| `music/game_over` | 10 to 20 s | `GameEnd`: everyone is down or out | Everyone in the game | None |
| `music/ambient` | 1 to 3 min, loops | Played quietly under the whole game, between the stings | Everyone in the game | None |
| `music/fire_sale` | 30 s, loops | While Fire Sale lasts, from every Mystery Box location | Nearby, from the boxes | None |

## Machines

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `perk/<perk_id>_jingle` | 15 to 30 s each | A lit vending machine now and then plays its perk's jingle (vigorade, reload, hellfire, saints_brew, jockey_energy, off_hand) | Nearby, from the machine | None |
| `mystery_box/spin` | 4 to 6 s | `MysteryBoxOpened`: while the weapons cycle | Nearby, from the box | Chest open and close |
| `mystery_box/move` | 3 to 5 s | `MysteryBoxMoved`: the box leaves (CoD's teddy-bear laugh) | Nearby, from the old place | None |
| `pap/upgrade` | 3 to 5 s | `PapUsed`: the upgrader takes and returns a weapon | Nearby, from the machine | None |
| `power/on` | 2 to 4 s | `PowerOn`: the power switch is thrown, a heavy clunk and rising hum | Everyone in the game | None |
| `trap/activate` | 1 to 2 s | `TrapActivated` | Nearby, from the controller | None |
| `purchase/denied` | under 1 s | Any purchase refused for too few points | The buyer | None |
| `door/debris_clear` | 1 to 2 s | `DoorOpened`, for debris | Nearby, from the debris | Ender eye launch |

## Power-ups

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `powerup/spawn` | under 1 s | `PowerUpSpawned`: a power-up appears | Nearby, from the drop | None |
| `powerup/loop` | 2 to 4 s, loops | While a power-up floats on the ground | Nearby, from the drop | None |
| `powerup/grab` | under 1 s | `PowerUpCollected`: the pickup sting | Everyone in the game | Beacon activate |
| `announcer/<powerup_id>` | 1 to 2 s each | `PowerUpCollected`: the announcer names it (max_ammo, insta_kill, double_points, nuke, carpenter, fire_sale) | Everyone in the game | None |
| `powerup/timer_end` | under 1 s | Insta-Kill, Double Points, or Fire Sale running out | Everyone in the game | None |

## Players

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `player/downed` | 1 to 2 s | `PlayerDowned` | Everyone in the game | None |
| `player/bleeding` | 1 to 2 s, loops | While a player is down: heartbeat and ringing | The downed player | None |
| `player/revived` | 1 to 2 s | `PlayerRevived` | Nearby, from the player | None |
| `player/bled_out` | 1 to 3 s | `PlayerBledOut` | Everyone in the game | None |

## Zombies

| Path | Length | Trigger | Heard by | Stand-in |
|---|---|---|---|---|
| `zombie/spawn` | 1 to 2 s | `ZombieSpawned`: a zombie climbs out at its spawn | Nearby, from the spawn | None |
| `zombie/ambient`, `zombie/sprint`, `zombie/attack`, `zombie/death` | 1 to 2 s each, several of each | Game zombies' voices in place of vanilla's, walking and sprinting apart | Nearby, from the zombie | Vanilla zombie sounds |
| `hound/spawn` | 1 to 2 s | A hellhound appears (CoD's lightning strike) | Nearby, from the spawn | None |
| `window/rebuild` | under 1 s | A board goes back on a window | Nearby, from the window | Armor stand break |
