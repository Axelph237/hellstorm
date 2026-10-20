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
