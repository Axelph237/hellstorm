# Zombies More test pack

A datapack for trying Hellstorm with [Zombies More](https://www.curseforge.com/minecraft/mc-mods/zombie-more) (`zombiesmore`, which needs GeckoLib) and [[TACZ] LesRaisins Tactical Equipements](https://www.curseforge.com/minecraft/mc-mods/tacz-lesraisins-tactical-equipements) (`lrtactical`).

## What it does

- Adds five Zombies More zombies as Hellstorm mob profiles, each at weight 1.
- Raises Hellstorm's own zombie (`hellstorm:zombie`) from weight 10 to 100. Each modded zombie is then about 1 spawn in 105, and the five together about 1 in 21.

| Profile | Mob | Settings |
| --- | --- | --- |
| `zombies_more_test:tank_zombie` | Tank Zombie | 4 times the health, 0.75 times the speed, hits for 8 |
| `zombies_more_test:explosive_zombie` | Explosive Zombie | `augment`, so it keeps throwing dynamite. Explosions in a match break no blocks. |
| `zombies_more_test:crawler` | Crawler | 0.6 times the health, 1.2 times the speed, hits for 3. It's a spider underneath and may climb walls. |
| `zombies_more_test:cursed_zombie` | Cursed Zombie | Default |
| `zombies_more_test:boomer_zombie` | Boomer Zombie | 1.5 times the health |

## Install

1. Copy this folder into your world's `datapacks/` folder.
2. Run `/reload`, or reopen the world.

## The starting kit

The starting kit is a server setting, not part of a datapack. In the world's `serverconfig/hellstorm-server.toml`, under `[players]`, set:

```toml
starting_guns = ["tacz:glock_17"]
starting_items = ["lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}"]
```

Then restart the world, or edit the file while the world is closed.

## Check it

- `/hs mobtest zombies_more_test:tank_zombie` spawns one profile in front of you, to see a mob without waiting for it to come up. Try each profile.
- Start a game. Players get the Glock and the dagger. A modded zombie turns up roughly once in 21 spawns.
