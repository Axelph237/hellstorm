# Sessions and multiplayer

## Goal

Co-op works on dedicated servers from day one. Joining and leaving a game never loses a player's normal inventory, and late joiners and disconnects are handled cleanly.

## Data shape

```java
record InventorySnapshot(UUID player, CompoundTag inventory, CompoundTag effects, GameType gameMode, Vec3 pos, ResourceKey<Level> dim) {}
```

Snapshots are stored in `MapSavedData` so they survive a crash.

## Implementation

- **Joining** (`/pap join` or a join pad block):
  1. Save an `InventorySnapshot`.
  2. Clear the inventory and effects. The player stays where they are until the game starts; the countdown ending moves everyone to the start zone in adventure mode.
  3. Give the starting loadout (configurable `ItemSpec` list, default a TacZ pistol and a knife).
  4. Set starting points.
- **Late join.** Joining during `RoundActive` puts the player in `Spectating(respawnNextRound = true)`.
- **Leaving** (`/pap leave`, game over, or kick): restore the snapshot exactly and delete it.
- **Disconnect.** The player's state is kept for `sessions.reconnect_grace_ticks`. Reconnecting in time restores their game state. After that they are removed from the game, and their snapshot is restored on their next login.
- **Crash.** On server start, any leftover snapshot is restored the next time that player logs in.
- **Server authority.** Every purchase, revive, and pickup is decided on the server. Clients only send intent packets ("use pressed on block X"), which the server validates.
- **Packets.** One `SimpleChannel` with protocol version checks. Broadcast scope is the players in that game, never `PacketDistributor.ALL`.

## Config surface

- `sessions.starting_loadout` (list of `ItemSpec`).
- `sessions.reconnect_grace_ticks` (1200).
- `sessions.max_players` (4, map overridable).

## Assets

None.

## Depends on

`game-loop-and-rounds.md`, `economy-and-points.md`, `configuration.md`.

## Verification

- GameTest: join then leave restores the exact inventory, effects, game mode, and position.
- In game with 2 clients: one disconnects mid-round and reconnects within the grace time with the same points and perks.
- Kill the server mid-game, restart, log in: the player's normal inventory comes back.
- Two maps in two dimensions run two games at once without sharing state.

## Phase

2 (join and leave), 7 (edge cases).

## As built (phase 7)

- **Joining and leaving** (built in phase 2): `/pap join` saves a snapshot (`game/Sessions`: inventory, effects, game mode, health, food, XP, position) once and clears the player; leaving, game end, or the next login outside a game restores it exactly.
- **Late join.** Joining during a round or the break spectates until the next round (`Game.join`).
- **Player cap.** `players.max_players` (4). `/pap join` on a full game says so; `Game.join` refuses too.
- **Disconnect grace** (`Game.disconnect`, `Game.reconnect`, `players.reconnect_grace_ticks`, 1200 = one minute).
  - In the lobby or after game over, a disconnect simply leaves the game.
  - Otherwise the player keeps their place and points, and their perks and game inventory stay on their saved player. Someone down becomes a spectator instead of bleeding out unseen, and spectators who are away aren't respawned at the next round. Someone up still counts as fighting, so a dropped connection doesn't end the game by itself.
  - Back within the grace time, they carry on: points as they left them, the game's adventure mode put back (a server may log them in with its own, a LAN world's default for one), or spectator mode if they were out. After it, `Game.tick` removes them; their next login outside a game restores their snapshot, which also takes away the game's perks.
  - On logout nobody is saved crawling (`Downs.standUp`), and only someone who left the game for good loses their perks then.
- **Not hurt outside the fight.** In the lobby, the countdown, and after game over, game players take no damage (`Game.protectsPlayers`).
- **Tests.** JUnit `DisconnectTest`: back in time keeps the place and points; outlasting the grace time removes them; someone away still counts as fighting, until the grace runs out; someone down who drops spectates and isn't respawned while away; the lobby just leaves; a full game takes no more. Checked live over LAN with two clients: a player with 4,821 points disconnected mid-round, was kept (offline, 4,821, still alive) while the round went on, reconnected five seconds later into the same game with the same points and the HUD, in adventure mode.

