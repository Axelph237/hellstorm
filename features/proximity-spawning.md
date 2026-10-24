# Proximity spawning

## Goal

Zombies spawn only in rooms the players have found, never in a part of the map nobody has been to.

## Data shape

- Each spawn marker feeds one zone: the zone it stands in, or, outside every zone, the zone behind the window it stands in front of (`map/SpawnLinks`, resolved once per game into `GameMap.spawnLinks`).
- `Game.discovered`: the set of zones discovered this game. It only grows.

## Implementation

1. **Discovery.** A game starts with its start zones discovered. Every tick after the countdown, each `Alive` player's zone (from `ZoneIndex`) is added. Downed players, spectators, and players still waiting discover nothing. A zone stays discovered until the game ends.
2. **Candidates.** `SpawnDirector.candidates`: the markers whose zone is discovered. Doors play no part. A bought door unlocks rooms for pathing, but their markers stay quiet until someone walks in.
3. **Weights.** A distance falloff to the nearest `Alive` player: `1 / (1 + d / spawning.falloff_blocks)`. Markers closer than `spawning.min_distance` to a player are skipped, unless every candidate is that close.
4. **Pick and spawn.** If `alive < max_alive` and the spawn cooldown is 0, pick a weighted marker, pick a mob profile allowed there (see `mob-profiles-and-ai-injection.md`), and spawn. Decrement `toSpawn`.
5. **Emit** `zombie_spawned`.

If no discovered zone has a marker, nothing spawns, the round waits, and a WARNING is logged once per game.

## Config surface

- `spawning.falloff_blocks` (default 24).
- `spawning.min_distance` (default 6).

## Assets

- Spawn marker block model uses a vanilla texture (for example `minecraft:block/spawner`). It is invisible while a game runs on its level (see `map-export-import.md`, "As built (spawn markers hide during a game)").

## Depends on

`map-model-and-zones.md`, `game-loop-and-rounds.md`, `windows-and-barricades.md`, `mob-profiles-and-ai-injection.md`.

## Verification

- JUnit `SpawnDirectorTest`: only discovered zones' markers are candidates, however far away; weights fall off with distance; markers inside `min_distance` are skipped unless all are.
- JUnit `GameTest`: only the start zone is discovered at first; a zone a player stood in stays discovered after they leave, and a zone they skipped stays undiscovered; a spectator discovers nothing; opening a door discovers nothing.
- GameTest `DoorGameTests.aRoomNobodyHasEnteredSpawnsNothingEvenWithItsDoorBoughtUntilAPlayerWalksIn`.
- In game: buy a door, stay out of the room behind it, and see no zombies come from it; walk in and back out, and see them start.

## Phase

2.

## As built

- **Discovered rooms, not nearby rooms.** Spawning used to follow the players: markers were active in each player's zone and in unlocked zones within `spawning.adjacent_hops` (1) connections of it, with a fallback to the start zone's markers. That tied spawning to doors twice over (a zone had to be unlocked, and hops ran through open doors), and a zone next to a bought door spawned zombies before anyone had seen it. Now a game keeps `Game.discovered` (start zones, plus every zone an `Alive` player has stood in), and a marker spawns only if its zone is in it. `adjacent_hops`, `SpawnDirector.activeZones`, `PlayerState.lastZone`, and `GameMap.startZoneMarkers` are gone. `Game.unlocked()` still exists, for pathing and the `door_opened` event, but spawning doesn't read it.
- **Distance weighting stays.** Under the new rule a discovered zone can be anywhere on the map, often far behind the players, so without the falloff most zombies would spawn far away and take a long walk in. The falloff keeps spawns near the players without ruling out any discovered zone. `min_distance` still keeps zombies from appearing in a player's face.
- **No start-room fallback.** It existed for players standing where no active zone had a marker. Start zones are always discovered now, so the fallback reduced to one case, every marker within `min_distance`, and `SpawnDirector.candidates` keeps those markers rather than stalling the round.
- **Where discovery runs.** `Game.tick` discovers before advancing the phase, in every phase after the lobby. The `Alive` check is what limits it: nobody is `Alive` during the countdown, and only players actually in play count. A player who walks into a room during the break between rounds discovers it.
- **Tests.** `chase` in `WindowGameTests` spawns its zombie in a room the player never enters, so both its rooms are start rooms.
- **Only into ticking chunks** (2026-10-24). `ForgeGameWorld.spawnZombie` spawns nothing at a marker whose chunk isn't ticking entities (`isPositionEntityTicking`, not `isLoaded`): a mob added to a chunk still being promoted is stored but hidden, and one past the simulation distance never moves, and either held the round open. `Game.tickRound` tries again after its cooldown. GameTests whose arenas reach past their floor wait for those chunks (`TestLayout.onceTicking`).
